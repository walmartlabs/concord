def test(ctx, x):
    print 'Hello from another module!'
    ctx.setVariable('y', x + 3)
