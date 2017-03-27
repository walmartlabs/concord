function doSomething(i) {
    return i * 3;
}

var x = execution.getVariable("input");
execution.setVariable("output", doSomething(x));