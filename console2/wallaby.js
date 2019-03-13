module.exports = function(wallaby) {
    return {
        files: ['src/**/*.ts*', '!src/**/*.test.ts*'],
        tests: ['src/**/*test.ts*'],
        env: {
            type: 'node',
            runner: 'node'
        },
        compilers: {
            '**/*.ts?(x)': wallaby.compilers.typeScript({
                module: 'commonjs',
                jsx: 'React'
            })
        },
        testFramework: 'jest',
        debug: true,
        setup: function(wallaby) {
            
            // you can access 'window' object in a browser environment,
            // 'global' object or require(...) something in node environment
        }
    };
};
