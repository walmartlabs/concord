def test(result, x):
    print 'Hello from another module!'
    result.set('y', x + 3)
    return

def test2(x):
    print 'Hello from another module!'
    return x + 3
