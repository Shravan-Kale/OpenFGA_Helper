import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.*;

public class convert {

    public static void main(String[] args) {
        String dsl = """
                model
                  schema 1.1

                type organization
                  relations
                    define member: [user] or owner
                    define owner: [user]
                    define repo_admin: [user, organization#member]
                    define repo_reader: [user, organization#member]
                    define repo_writer: [user, organization#member]

                type repo
                  relations
                    define admin: [user, team#member] or repo_admin from owner
                    define maintainer: [user, team#member] or admin
                    define owner: [organization]
                    define reader: [user, team#member] or triager or repo_reader from owner
                    define triager: [user] but not repo_reader from owner
                    define writer: [user, user:*, team#member] or maintainer or repo_writer from owner
                    
                type team
                  relations
                    define member: [user, team#member]

                type user
                """;

        Map<String, Object> dslJson = parseDsl(dsl);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonOutput = gson.toJson(dslJson);
        System.out.println(jsonOutput);
    }

    public static Map<String, Object> parseDsl(String dsl) {
        String[] lines = dsl.strip().split("\n");
        String schemaVersion = null;
        List<Map<String, Object>> types = new ArrayList<>();
        Map<String, Object> currentType = null;

        for (String line : lines) {
            line = line.strip();
            if (line.startsWith("schema")) {
                schemaVersion = line.split(" ")[1];
            } else if (line.startsWith("type")) {
                if (currentType != null) {
                    if (((Map<?, ?>) currentType.get("relations")).isEmpty()) {
                        currentType.put("metadata", null);
                    }
                    types.add(currentType);
                }
                currentType = new HashMap<>();
                currentType.put("type", line.split(" ")[1]);
                currentType.put("relations", new HashMap<String, Object>());
                currentType.put("metadata", new HashMap<String, Object>());
                ((Map<String, Object>) currentType.get("metadata")).put("relations", new HashMap<String, Object>());
            } else if (line.startsWith("define")) {
                String[] parts = line.split(":", 2);
                String relationName = parts[0].split(" ")[1];
                String relationDef = parts[1].strip();
                ((Map<String, Object>) currentType.get("relations")).put(relationName, parseRelationDef(relationDef));
                ((Map<String, Object>) ((Map<String, Object>) currentType.get("metadata")).get("relations")).put(relationName, parseMetadataRelationDef(relationDef));
            }
        }

        if (currentType != null) {
            if (((Map<?, ?>) currentType.get("relations")).isEmpty()) {
                currentType.put("metadata", null);
            }
            types.add(currentType);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("schema_version", schemaVersion);
        result.put("type_definitions", types);
        return result;
    }

    public static Map<String, Object> parseRelationDef(String definition) {
        Map<String, Object> result = new HashMap<>();
        if (definition.contains(" or ")) {
            String[] parts = definition.split(" or ");
            List<Map<String, Object>> children = new ArrayList<>();
            for (String part : parts) {
                children.add(parseRelationDef(part.strip()));
            }
            result.put("union", Map.of("child", children));
        } else if (definition.contains(" and ")) {
            String[] parts = definition.split(" and ");
            List<Map<String, Object>> children = new ArrayList<>();
            for (String part : parts) {
                children.add(parseRelationDef(part.strip()));
            }
            result.put("intersection", Map.of("child", children));
        } else if (definition.contains(" but not ")) {
            String[] parts = definition.split(" but not ");
            result.put("difference", Map.of(
                    "base", parseRelationDef(parts[0].strip()),
                    "subtract", parseRelationDef(parts[1].strip())
            ));
        } else if (definition.contains("[") && definition.contains("]")) {
            result.put("this", new HashMap<>());
        } else if (definition.contains(" from ")) {
            String[] parts = definition.split(" from ");
            result.put("tupleToUserset", Map.of(
                    "tupleset", Map.of("object", "", "relation", parts[1].strip()),
                    "computedUserset", Map.of("object", "", "relation", parts[0].strip())
            ));
        } else {
            result.put("computedUserset", Map.of("object", "", "relation", definition));
        }
        return result;
    }

    public static Map<String, Object> parseMetadataRelationDef(String definition) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> directlyRelatedUserTypes = new ArrayList<>();
        if (definition.contains("[") && definition.contains("]")) {
            int start = definition.indexOf('[') + 1;
            int end = definition.indexOf(']');
            String[] definitions = definition.substring(start, end).split(",");
            for (String defn : definitions) {
                defn = defn.strip();
                if (defn.contains(":")) {
                    directlyRelatedUserTypes.add(Map.of("type", defn.split(":")[0], "wildcard", new HashMap<>()));
                } else if (defn.contains("#")) {
                    String[] parts = defn.split("#");
                    directlyRelatedUserTypes.add(Map.of("type", parts[0], "relation", parts[1]));
                } else {
                    directlyRelatedUserTypes.add(Map.of("type", defn));
                }
            }
        }
        result.put("directly_related_user_types", directlyRelatedUserTypes);
        return result;
    }
}
