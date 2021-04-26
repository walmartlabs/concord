// get a variable
def v = execution.variables().get('myVar')
println('Hello ' + myVar)

// set a variable
execution.variables().set('newVar', 'Hello, world!')
println(newVar)
