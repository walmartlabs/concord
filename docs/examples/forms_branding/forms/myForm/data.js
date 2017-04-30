data = {
    "submitUrl" : "/api/service/custom_form/6cf435f0-b85c-42ea-a454-1ac308aff8c3/9fe117b3-5dec-4de2-afe7-1765a0e4e305/continue",
    "success" : false,
    "definitions" : {
        "firstName" : {
            "type" : "string",
            "cardinality" : "ONE_AND_ONLY_ONE"
        },
        "lastName" : {
            "type" : "string",
            "cardinality" : "ONE_AND_ONLY_ONE"
        },
        "color" : {
            "type" : "string",
            "cardinality" : "ONE_AND_ONLY_ONE",
            "allow" : [ "red", "green", "blue" ]
        },
        "age" : {
            "type" : "int",
            "cardinality" : "ONE_AND_ONLY_ONE"
        }
    },
    "values" : {
        "lastName" : "Smith",
        "age": 21,
        "color": "green"
    }
};