import json

dsl = """
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
"""


def parse_dsl(dsl):
    lines = dsl.strip().split('\n')
    schema_version = None
    types = []
    current_type = None

    for line in lines:
        line = line.strip()
        if line.startswith('schema'):
            schema_version = line.split()[1]
        elif line.startswith('type'):
            if current_type:
                if not current_type["relations"]:
                    current_type["metadata"] = None
                types.append(current_type)
            current_type = {
                "type": line.split()[1],
                "relations": {},
                "metadata": {
                    "relations": {}
                }
            }
        elif line.startswith('define'):
            parts = line.split(':', 1)  # Split only at the first ':'
            relation_name = parts[0].split()[1]
            relation_def = parts[1].strip()
            current_type["relations"][relation_name] = parse_relation_def(relation_def)
            current_type["metadata"]["relations"][relation_name] = parse_metadata_relation_def(relation_def)
    if current_type:
        if not current_type["relations"]:
            current_type["metadata"] = None
        types.append(current_type)

    return {
        "schema_version": schema_version,
        "type_definitions": types
    }


def parse_relation_def(definition):
    if ' or ' in definition:
        parts = definition.split(' or ')
        return {
            "union": {
                "child": [parse_relation_def(part.strip()) for part in parts]
            }
        }

    if ' and ' in definition:
        parts = definition.split(' and ')
        return {
            "intersection": {
                "child": [parse_relation_def(part.strip()) for part in parts]
            }
        }

    if ' but not ' in definition:
        parts = definition.split(' but not ')
        return {
            "difference": {
                "base": parse_relation_def(parts[0].strip()),
                "subtract": parse_relation_def(parts[1].strip())
            }
        }

    if '[' in definition and ']' in definition:
        return {"this": {}}

    if ' from ' in definition:
        before_from, after_from = definition.split(' from ')
        return {
            "tupleToUserset": {
                "tupleset": {
                    "object": "",
                    "relation": after_from.strip()
                },
                "computedUserset": {
                    "object": "",
                    "relation": before_from.strip()
                }
            }
        }

    return {
        "computedUserset": {
            "object": "",
            "relation": definition
        }
    }


def parse_metadata_relation_def(definition):
    if '[' in definition and ']' in definition:
        start = definition.index("[") + 1
        end = definition.index("]")
        definitions = definition[start:end].split(',')
        directly_related_user_types = []
        for defn in definitions:
            defn = defn.strip()
            if ':' in defn:
                directly_related_user_types.append({"type": defn.split(':')[0], "wildcard": {}})
            elif '#' in defn:
                type_part, relation_part = defn.split('#')
                directly_related_user_types.append({"type": type_part, "relation": relation_part})
            else:
                directly_related_user_types.append({"type": defn})
        return {"directly_related_user_types": directly_related_user_types}
    else:
        return {"directly_related_user_types": []}


dsl_json = parse_dsl(dsl)
print(json.dumps(dsl_json, indent=2))
