def test(ctx, x):
    print 'Hello from another module!'
    ctx.variables().set('y', x + 3)
    return

def test2(x):
    print 'Hello from another module!'
    return x + 3
