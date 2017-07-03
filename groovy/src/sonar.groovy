/**
 * Created by ajanoni on 31/05/17.
 *
 *
 * ####################################################################
 * !!!!! IMPORTANT !!!!!
 * If you are running it from IntelliJ:
 * Alt+Enter with a caret positioned on @Grab to download artifacts.
 * ####################################################################
 *
 */


@Grab('com.opencsv:opencsv:3.9')
@Grab('com.sun.jersey:jersey-bundle:1.19.3')
@Grab('org.json:json:20170516')
@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7')

import com.opencsv.CSVWriter
import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.api.client.WebResource
import com.sun.jersey.api.client.config.ClientConfig
import com.sun.jersey.api.client.config.DefaultClientConfig
import com.sun.jersey.api.json.JSONConfiguration
import groovy.transform.EqualsAndHashCode
import groovyx.net.http.ContentType
import groovyx.net.http.Method
import groovyx.net.http.RESTClient
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.nio.charset.StandardCharsets

@EqualsAndHashCode(excludes = 'col')
class Violation {
    String projectName
    String file
    Integer line
    Integer col
}

//queryS2444 = "MATCH (nField:FieldDeclaration)-[:tree_edge]->(nVar:VariableDeclarationFragment)<-[:SETS]-(nMethod:MethodDeclaration) MATCH (nMethod)-[:tree_edge*]->(var)<-[:SET_BY]-(nVar) WHERE nField.modifiers CONTAINS 'static' AND NOT nField.modifiers CONTAINS 'volatile' AND NOT nField.modifiers CONTAINS 'final' AND NOT nMethod.modifiers CONTAINS 'synchronized' AND NOT (var)<-[:tree_edge*]-(:SynchronizedStatement) AND NOT (nField)-[:tree_edge*]->(:PrimitiveType) RETURN DISTINCT var.col AS col, var.line AS line, var.file AS file ORDER BY var.file, var.line";

// queryS1143 = "MATCH (n:ReturnStatement)<-[:tree_edge*]-(:Block)<-[:finally]-(:TryStatement) " +
//        "RETURN DISTINCT n.col AS col, n.line AS line, n.file AS file " +
//        "UNION " +
//        "MATCH (n:BreakStatement)<-[:tree_edge*]-(:Block)<-[:finally]-(:TryStatement) " +
//        "RETURN DISTINCT n.col AS col, n.line AS line, n.file AS file " +
//        "UNION " +
//        "MATCH (n:ContinueStatement)<-[:tree_edge*]-(:Block)<-[:finally]-(:TryStatement) " +
//        "RETURN DISTINCT n.col AS col, n.line AS line, n.file AS file " +
//        "UNION " +
//        "MATCH (n:ThrowStatement)<-[:tree_edge*]-(:Block)<-[:finally]-(:TryStatement) " +
//        "RETURN DISTINCT n.col AS col, n.line AS line, n.file AS file"

//queryS1444 = "MATCH (c)-[:tree_edge*]->(field:FieldDeclaration) " +
//        "WHERE field.modifiers CONTAINS ('static') " +
//        "AND field.modifiers CONTAINS ('public') " +
//        "AND NOT field.modifiers CONTAINS ('final') " +
//        "AND NOT c.entity_type = 'interface' " +
//        "WITH COLLECT(DISTINCT(field)) AS All " +
//        "OPTIONAL MATCH (field)<-[:member]-(:TypeDeclaration)-[:annotation]->(sma:SingleMemberAnnotation) " +
//        "WHERE sma.name = 'StaticMetamodel' " +
//        "WITH COLLECT(DISTINCT(field)) AS SmaClasses, All " +
//        "WITH FILTER(x IN All WHERE NOT x IN SmaClasses) AS FilteredResult " +
//        "UNWIND FilteredResult AS FinalResult " +
//        "MATCH(FinalResult)-[:fragment]->(vd:VariableDeclarationFragment) " +
//        "RETURN vd.col AS col, vd.line AS line, vd.file AS file"


queryS2184 =
            "// int/long to double/float cases\n" +
            "//CASE A\n" +
            "MATCH (s1:InfixExpression)<-[:tree_edge*0..]-(declaration)-[:type]->(varType)\n" +
            "WHERE (s1.operator = '/' OR s1.operator = '*')\n" +
            "AND (toLower(varType.name) = 'double' OR toLower(varType.name) = 'float')\n" +
            "AND NOT (s1)<-[:tree_edge*]-(:MethodInvocation)\n" +
            "WITH declaration AS i\n" +
            "OPTIONAL MATCH (i)-[:tree_edge*]->(:InfixExpression)-[:tree_edge*]->(varNumCast:CastExpression)-[:type]->(varType)\n" +
            "WHERE toLower(varType.name) = 'float' OR toLower(varType.name) = 'double'\n" +
            "WITH i, varType\n" +
            "OPTIONAL MATCH (i)-[:tree_edge*]->(:InfixExpression)-[:tree_edge*]->(varNumLiteral:NumberLiteral)\n" +
            "WHERE toLower(varNumLiteral.name) CONTAINS ('f')\n" +
            "OR toLower(varNumLiteral.name) CONTAINS ('d') \n" +
            "OR varNumLiteral.name CONTAINS ('.')\n" +
            "WITH i, varType, varNumLiteral\n" +
            "OPTIONAL MATCH (i)-[:tree_edge*]->(:InfixExpression)-[:tree_edge*]->(:SimpleName)<-[:USE_BY]-()<-[:tree_edge*0..]-()-[:type]->(varType3)\n" +
            "WHERE toLower(varType3.name) = 'float' OR toLower(varType3.name) = 'double'\n" +
            "WITH i, varType, varNumLiteral, varType3\n" +
            "OPTIONAL MATCH (i)-[:tree_edge*]->(:InfixExpression)-[:tree_edge*]->(:MethodInvocation)-[:tree_edge]->(mName:SimpleName)\n" +
            "WHERE mName.name = 'doubleValue' OR mName.name = 'floatValue' \n" +
            "WITH i, varType, varNumLiteral, varType3, mName\n" +
            "OPTIONAL MATCH (i:CastExpression)-[:type]->(varType4)\n" +
            "WHERE toLower(varType4.name) = 'float' OR toLower(varType4.name) = 'double'\n" +
            "WITH i, varType, varNumLiteral, varType3, mName, varType4\n" +
            "WHERE varType IS NULL AND varNumLiteral IS NULL AND varType3 IS NULL AND mName IS NULL AND varType4 IS NULL \n" +
            "RETURN DISTINCT i.col AS col, i.line AS line, i.file AS file\n" +
            "\n" +
            "UNION\n" +
            "\n" +
            "//methods with return type\n" +
            "//CASE B\n" +
            "MATCH (s2:InfixExpression)<-[:tree_edge*]-(:ReturnStatement)<-[:tree_edge*]-(:MethodDeclaration)-[:return]->(retVarType)\n" +
            "MATCH (s2)-[:tree_edge]->(:SimpleName)<-[:USE_BY]-()-[:type]->(varType)\n" +
            "WHERE (s2.operator = '/' OR s2.operator = '*') \n" +
            "AND (toLower(varType.name) = 'long' OR toLower(varType.name) = 'int')\n" +
            "AND (toLower(retVarType.name) = 'float' OR toLower(retVarType.name) = 'double')\n" +
            "WITH s2 AS i\n" +
            "OPTIONAL MATCH (i)-[:tree_edge*0..]->(:InfixExpression)-[:tree_edge*]->(varNumCast:CastExpression)-[:type]->(varType)\n" +
            "WHERE toLower(varType.name) = 'float' OR toLower(varType.name) = 'double'\n" +
            "WITH i, varType\n" +
            "OPTIONAL MATCH (i)-[:tree_edge*0..]->(:InfixExpression)-[:tree_edge*]->(varNumLiteral:NumberLiteral)\n" +
            "WHERE toLower(varNumLiteral.name) CONTAINS ('f')\n" +
            "OR toLower(varNumLiteral.name) CONTAINS ('d') \n" +
            "OR varNumLiteral.name CONTAINS ('.')\n" +
            "WITH i, varType, varNumLiteral\n" +
            "OPTIONAL MATCH (i)-[:tree_edge*0..]->(:InfixExpression)-[:tree_edge*]->(:SimpleName)<-[:USE_BY]-()<-[:tree_edge*0..]-()-[:type]->(varType3)\n" +
            "WHERE toLower(varType3.name) = 'float' OR toLower(varType3.name) = 'double'\n" +
            "WITH i, varType, varNumLiteral, varType3\n" +
            "OPTIONAL MATCH (i)-[:tree_edge*0..]->(:InfixExpression)-[:tree_edge*]->(:MethodInvocation)-[:tree_edge]->(mName:SimpleName)\n" +
            "WHERE mName.name = 'doubleValue' OR mName.name = 'floatValue' \n" +
            "WITH i, varType, varNumLiteral, varType3, mName\n" +
            "OPTIONAL MATCH (i:CastExpression)-[:type]->(varType4)\n" +
            "WHERE toLower(varType4.name) = 'float' OR toLower(varType4.name) = 'double'\n" +
            "WITH i, varType, varNumLiteral, varType3, mName, varType4\n" +
            "WHERE varType IS NULL AND varNumLiteral IS NULL AND varType3 IS NULL AND mName IS NULL AND varType4 IS NULL \n" +
            "RETURN DISTINCT i.col AS col, i.line AS line, i.file AS file\n" +
            "\n" +
            "//end int/long to double/float cases\n" +
            "\n" +
            "\n" +
            "UNION\n" +
            "\n" +
            "// int to long cases\n" +
            "//CASE C\n" +
            "MATCH (s1:InfixExpression)<-[:tree_edge*0..]-(declaration)-[:type]->(varType)\n" +
            "WHERE s1.operator = '*'\n" +
            "AND toLower(varType.name) = 'long'\n" +
            "AND NOT (declaration:CastExpression)\n" +
            "AND NOT (s1)-[:tree_edge]->(:QualifiedName)\n" +
            "WITH s1 as exp1, declaration AS i\n" +
            "OPTIONAL MATCH (i)-[:tree_edge*]->(varNumCast:CastExpression)-[:type]->(varType2)\n" +
            "WHERE toLower(varType2.name) = 'long'\n" +
            "WITH exp1, i, varNumCast\n" +
            "OPTIONAL MATCH (i)-[:tree_edge*]->(:InfixExpression)-[:tree_edge*]->(varNumLiteral:NumberLiteral)\n" +
            "WHERE toLower(varNumLiteral.name) CONTAINS ('l')\n" +
            "OPTIONAL MATCH (i)-[:tree_edge*]->(:InfixExpression)-[:tree_edge*]->(:SimpleName)<-[:USE_BY]-()<-[:tree_edge*0..]-()-[:type]->(varType3)\n" +
            "WHERE toLower(varType3.name) = 'long'\n" +
            "WITH exp1, i, varNumCast, varNumLiteral, varType3\n" +
            "OPTIONAL MATCH (i)-[:tree_edge*]->(:InfixExpression)-[:tree_edge*]->(:CastExpression)-[:type]->(varType4)\n" +
            "WHERE toLower(varType4.name) = 'long'\n" +
            "WITH exp1, i, varNumCast, varNumLiteral, varType3, varType4\n" +
            "OPTIONAL MATCH (i)-[:tree_edge*]->(:InfixExpression)-[:tree_edge]->(:MethodInvocation)-[:INVOKES]->(:MethodDeclaration)-[:return]-(varType5)\n" +
            "WHERE toLower(varType5.name) = 'long'\n" +
            "WITH exp1, i, varNumCast, varNumLiteral, varType3, varType4, varType5\n" +
            "OPTIONAL MATCH (i:ClassInstanceCreation)-[:type]->(classType:SimpleType)\n" +
            "WHERE toLower(classType.name) = 'long' OR toLower(classType.name) = 'double'\n" +
            "WITH exp1, i, varNumCast, varNumLiteral, varType3, varType4, varType5, classType\n" +
            "WHERE varNumCast IS NULL AND varNumLiteral IS NULL AND varType3 IS NULL AND varType4 IS NULL AND varType5 IS NULL AND classType IS NULL\n" +
            "RETURN DISTINCT exp1.col AS col, exp1.line AS line, exp1.file AS file\n" +
            "\n" +
            "\n" +
            "UNION\n" +
            "\n" +
            "//CASE D\n" +
            "MATCH (s2:InfixExpression)<-[:tree_edge*0..]-()-[:type]->(varType)\n" +
            "MATCH (s2)-[:tree_edge]->(qName:QualifiedName)\n" +
            "WHERE  (s2.operator = '+' OR s2.operator = '-')\n" +
            "AND toLower(varType.name) = 'long'\n" +
            "AND qName.name = 'Integer.MAX_VALUE' OR qName.name = 'Integer.MIN_VALUE'\n" +
            "WITH s2 AS i\n" +
            "OPTIONAL MATCH (i)-[:tree_edge]->(varNumCast:CastExpression)-[:type]->(varType)\n" +
            "WHERE toLower(varType.name) = 'long'\n" +
            "WITH i, varNumCast\n" +
            "OPTIONAL MATCH (i)-[:tree_edge]->(varNumLiteral:NumberLiteral)\n" +
            "WHERE varNumLiteral.name CONTAINS ('l') OR varNumLiteral.name CONTAINS ('L')\n" +
            "OPTIONAL MATCH (i)-[:tree_edge*]->(:SimpleName)<-[:USE_BY]-()<-[:tree_edge*]-()-[:type]->(varType3)\n" +
            "WHERE toLower(varType3.name) = 'long'\n" +
            "WITH i, varNumCast, varNumLiteral, varType3\n" +
            "OPTIONAL MATCH (i)<-[:tree_edge*]-(:CastExpression)-[:type]->(varType4)\n" +
            "WHERE toLower(varType4.name) = 'long'\n" +
            "WITH i, varNumCast, varNumLiteral, varType3, varType4\n" +
            "WHERE varNumCast IS NULL AND varNumLiteral IS NULL AND varType3 IS NULL AND varType4 IS NULL  \n" +
            "RETURN DISTINCT i.col AS col, i.line AS line, i.file AS file"


cgProjects = [
        'business-payment'           : 'a98c9c54-23c1-4dfc-a9d1-5335c1368af3',
        'versata-m1.ems'             : 'e494eb5f-5a45-4259-b2a7-714f16dbd6b1',
        'aurea-sonic-mq'             : '5beea8ba-c579-46f8-8c9e-c94124a4f12e',
        'ignite-sensage-analyzer'    : '28ade68a-dd2e-405e-a87a-549ecc0cf57d',
        'ta-smartleads-lms-mct'      : '223915ff-d7cc-4acc-9b23-6c238c77f39a',
        'aurea-aes-edi'              : '7fc8e251-9fca-4861-b245-8ccde4580f67',
        'pss'                        : 'aad7ba6b-8069-4aa3-8a36-38cc5c5e20d1',
        'kerio-mykerio-kmanager'     : 'cb7cd6df-a193-444f-8a17-633da0025a18',
        'aurea-lyris-platform-edge'  : '34267f92-0883-4004-bd33-1c570ab54552',
        'devfactory-codegraph-server': '0db9b092-2e84-438e-949e-5abaad903dad',
        'aurea-java-brp-cs-ruletest' : '6214fe83-68ab-49c1-9dda-3a34ccd18991'
]

String componentRoots(Map cgProjects) {
    String ret = "componentRoots="
    cgProjects.each {
        ret += "," + it.key
    }
    return ret;
}

fileLocal = "/Users/ajanoni/sonarcsv"

sonarBaseUrl = "http://brp-sonar.ecs.devfactory.com"

ruleId = "rules=squid%3AS2184" //CHANGE THE RULE NAME HERE

sonarUrl = sonarBaseUrl + "/api/issues/search?" + componentRoots(cgProjects) + "&" + ruleId

sonarViolation = toViolationDTO(findViolations())

cgViolation = getViolationFromCG(queryS2184) //CHANGE THE QUERY HERE

exportToCsv("S2184", sonarViolation, cgViolation) //CHANGE THE QUERY HERE

Set<Violation> getViolationFromCG(String query) {

    Set<Violation> retViolation = new HashSet<>()

    cgProjects.each { k, v ->
        def cgClient = new RESTClient('https://codegraph-api-prod.ecs.devfactory.com/api/1.0/graphs/' + v + '/query')
        cgClient.getClient().params.setParameter("http.connection.timeout", 20000)
        cgClient.getClient().params.setParameter("http.socket.timeout", 21000)
        println 'https://codegraph-api-prod.ecs.devfactory.com/api/1.0/graphs/' + v + '/query'
        retry(3, { e -> e.printStackTrace() }) {
            cgClient.request(Method.POST, ContentType.JSON) { req ->
                body = [query: query, querytype: 'cypher', resulttype: 'row']
                println 'request'
                response.success = { resp, json ->
                    json.results.data.row.each {
                        it.each {
                            retViolation.add(new Violation([projectName: k, file: it[2], line: it[1], col: it[0]]))
                        }
                    }
                }

                response.failure = { resp ->
                    println "Unexpected error: ${resp.statusLine.statusCode}"
                    println $ { resp.statusLine.reasonPhrase }
                }
            }
        }
    }
    return retViolation
}


void generateCsvPerRule() {

    violations = findViolations()
    violations.each {
        project, rule ->
            rule.each {
                name, violation -> println "Total violations: ${violation.size()}"
            }

    }
    exportToCsv(violations)

}

// projectName, <rule , violations <file, startLine, endLine, startOffset, endOffset, message, type>>
Map<String, Map<String, List<String[]>>> findViolations() throws JSONException {

    ClientConfig cc = new DefaultClientConfig();
    cc.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
    Client client = Client.create(cc);

    WebResource webResource = client.resource(sonarUrl)
            .queryParam("ps", String.valueOf(100))
            .queryParam("p", "1")
            .queryParam("statuses", "OPEN,REOPENED");
    ClientResponse response =
            webResource.accept("application/json").type("application/json").get(ClientResponse
                    .class);

    String body = response.getEntity(String.class);
    JSONObject jsonObj = new JSONObject(body);

    //println(String.format("Body %s", body));
    int total = jsonObj.getInt("total");
    int availablePages = (int) Math.ceil(total / 100);
    println(String.format("Available pages %d, and total %d", availablePages, total));

    return getMapResult(client, jsonObj, availablePages);
}

Map<String, Map<String, List<String[]>>> getMapResult(Client client, JSONObject jsonObj, int availablePages)
        throws JSONException {
    Map<String, Map<String, List<String[]>>> ruleViolations = new HashMap<>();
    ruleViolations = processResponse(ruleViolations, jsonObj);
    if (availablePages > 1) {
        for (int i = 2; i <= availablePages; i++) {
            println(String.format("Requesting page %d", i))
            WebResource webResource = client.resource(sonarUrl)
                    .queryParam("ps", String.valueOf(100))
                    .queryParam("p", String.valueOf(i));
            ClientResponse response = webResource.accept("application/json")
                    .type("application/json")
                    .get(ClientResponse.class);
            String body = response.getEntity(String.class);
            //println(String.format("paged Body is %s", body))
            JSONObject newBody = new JSONObject(body);
            ruleViolations = processResponse(ruleViolations, newBody);
        }
    }
    return ruleViolations;
}

Map<String, Map<String, List<String[]>>> processResponse(
        Map<String, Map<String, List<String[]>>> ruleViolations, JSONObject jsonObj) throws JSONException {
    JSONArray array = jsonObj.getJSONArray("issues");
    for (int i = 0; i < array.length(); i++) {
        JSONObject issueObj = (JSONObject) array.get(i);
        String projectName = (String) issueObj.get("project");
        String ruleName = (String) issueObj.get("rule");
        String file = (String) issueObj.get("component");
        //println(String.format("IssueObject is %s", issueObj))
        String[] newViolation = getStringsFromResponse(issueObj, file);
        Map<String, List<String[]>> ruleMap = ruleViolations.getOrDefault(projectName, new HashMap<>());
        List<String[]> violations = ruleMap.getOrDefault(ruleName, new ArrayList<>());
        violations.add(newViolation);
        ruleMap.put(ruleName, violations);
        ruleViolations.put(projectName, ruleMap);
        //println(String.format("New Violation for rule %s has been added for project %s", ruleName, projectName))
    }
    return ruleViolations
}

String[] getStringsFromResponse(JSONObject issueObj, String file) throws JSONException {
    String startLine = "";
    String endLine = "";
    String startOffset = "";
    String endOffset = "";
    if (issueObj.has("textRange")) {
        JSONObject textRange = (JSONObject) issueObj.get("textRange");
        startLine = String.valueOf(textRange.get("startLine"));
        endLine = String.valueOf(textRange.get("endLine"));
        if (textRange.has("startOffset")) {
            startOffset = String.valueOf(textRange.get("startOffset"));
        }
        if (textRange.has("endOffset")) {
            endOffset = String.valueOf(textRange.get("endOffset"));
        }
    }
    String message = issueObj.getString("message");
    String type = issueObj.getString("type");
    String project = issueObj.getString("project");

    String fileOk = file.replaceAll(project + ":", '')
    return [fileOk, startLine, endLine, startOffset, endOffset, message, type]
}

void exportToCsv(Map<String, Map<String, List<String[]>>> projectRuleViolations) {

    File rbf = new File(fileLocal);
    boolean diretoryCreated = rbf.mkdirs();
    if (diretoryCreated) {
        println(String.format("Directory %s has been created.", rbf))
    }
    for (Map.Entry<String, Map<String, List<String[]>>> entryProject : projectRuleViolations.entrySet()) {
        String projectName = entryProject.getKey();
        for (Map.Entry<String, List<String[]>> entryRuleViolation :
                entryProject.getValue().entrySet()) {
            String ruleName = entryRuleViolation.getKey();

            resultPath = rbf.toString() + "/" + projectName + "_" + ruleName + ".csv"
            Writer fileRbfWriter = new OutputStreamWriter(
                    new FileOutputStream(resultPath),
                    StandardCharsets.UTF_8)
            CSVWriter reportWriter = new CSVWriter(fileRbfWriter, (char) ",")

            List<String[]> violations = entryRuleViolation.getValue();
            println " >>> " + violations.size()
            for (String[] item : violations) {
                String[] reportLine = new String[3];
                reportLine[0] = item[3]; // col
                reportLine[1] = item[1]; // line
                reportLine[2] = item[0]; //file
                reportWriter.writeNext(reportLine);
            }

            reportWriter.flush();
            reportWriter.close();
        }

    }
}


void exportToCsv(String ruleName, Set<Violation> sonarViolation, Set<Violation> cgViolation) {
    if (sonarViolation.isEmpty() && cgViolation.isEmpty()) {
        return;
    }

    def intersectViolation = sonarViolation.intersect(cgViolation)
    def onlyCG = cgViolation - sonarViolation
    def onlySonar = sonarViolation - cgViolation


    File rbf = new File(fileLocal);
    boolean diretoryCreated = rbf.mkdirs();
    if (diretoryCreated) {
        println(String.format("Directory %s has been created.", rbf))
    }

    resultPath = rbf.toString() + "/ALL_" + ruleName + ".csv"
    Writer fileRbfWriter = new OutputStreamWriter(
            new FileOutputStream(resultPath),
            StandardCharsets.UTF_8)
    CSVWriter reportWriter = new CSVWriter(fileRbfWriter, (char) ",")


    List<String[]> resultCsv = new ArrayList<>()
    intersectViolation.each {
        String[] reportLine = new String[7];
        reportLine[0] = it.projectName
        reportLine[1] = it.col
        reportLine[2] = it.line
        reportLine[3] = it.file
        reportLine[4] = it.col
        reportLine[5] = it.line
        reportLine[6] = it.file
        resultCsv.add(reportLine)
    }

    onlySonar.each {
        String[] reportLine = new String[7];
        reportLine[0] = it.projectName
        reportLine[1] = it.col
        reportLine[2] = it.line
        reportLine[3] = it.file
        reportLine[4] = '0'
        reportLine[5] = '0'
        reportLine[6] = it.file
        resultCsv.add(reportLine)
    }

    onlyCG.each {
        String[] reportLine = new String[7];
        reportLine[0] = it.projectName
        reportLine[1] = '0'
        reportLine[2] = '0'
        reportLine[3] = it.file
        reportLine[4] = it.col
        reportLine[5] = it.line
        reportLine[6] = it.file
        resultCsv.add(reportLine)
    }

    resultCsv.sort { a, b -> a[0] <=> b[0] ?: a[6] <=> b[6] ?: a[3] <=> b[3] ?: a[5] <=> b[5] ?: a[2] <=> b[2] ?: a[4] <=> b[4] ?: a[1] <=> b[1] }
    reportWriter.writeNext((String[]) ['projectName', 'sonarCol', 'sonarLine', 'sonarFile', 'cgCol', 'cgLine', 'cgFile'])
    reportWriter.writeAll(resultCsv)
    reportWriter.flush();
    reportWriter.close();

    println "===== Files not found in Sonar ====="
    println cgViolation.findAll {
        !sonarViolation.file.contains(it.file)
    }.file

    println "===== Files not found in CG ====="
    println sonarViolation.findAll {
        !cgViolation.file.contains(it.file)
    }.file

    def countCg = cgViolation.countBy { it.file }
    def countSonar = sonarViolation.countBy { it.file }

    countCg.intersect(countSonar).each {
        countCg.remove(it.key);
        countSonar.remove(it.key)
    }

    println "COUNTCG"
    println countCg

    println "COUNTSONAR"
    println countSonar

}


Set<Violation> toViolationDTO(Map<String, Map<String, List<String[]>>> projectRuleViolations) {
    Set<Violation> retViolationDTO = new HashSet<>()
    for (Map.Entry<String, Map<String, List<String[]>>> entryProject : projectRuleViolations.entrySet()) {
        String projectName = entryProject.getKey();
        for (Map.Entry<String, List<String[]>> entryRuleViolation :
                entryProject.getValue().entrySet()) {
            String ruleName = entryRuleViolation.getKey();

            List<String[]> violations = entryRuleViolation.getValue();
            for (String[] item : violations) {
                retViolationDTO.add(new Violation([projectName: projectName, file: item[0], line: item[1].toInteger(), col: item[3].toInteger()]))
            }
        }
    }
    return retViolationDTO
}

def retry(int times = 5, Closure errorHandler = { e -> log.warn(e.message, e) }
          , Closure body) {
    int retries = 0
    def exceptions = []
    while (retries++ < times) {
        try {
            println "Attempt:" + retries
            return body.call()
        } catch (e) {
            exceptions << e
            errorHandler.call(e)
        }
    }
    throw new Exception("Failed after $times retries")
}
