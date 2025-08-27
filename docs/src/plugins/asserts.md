# Asserts Plugin

The `asserts` task allows you to verify conditions within your flows. 
It ensures that required variables, inputs, or states are correctly set during process execution. 
If a condition fails, the flow will terminate with an error, preventing further execution

Task provides the following functions:

- `asserts.hasVariable(variableName)` - verifies that a specific variable is present in the process; 
- `asserts.hasFile(path)` - checks if a file exists at the given path;
- `asserts.assertEquals(expected, actual)` - ensures that two values are equal;
- `asserts.assertTrue(condition)` - validates that a given condition is true.
