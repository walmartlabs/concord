module.exports = function override(config, env) {

    if (!config.plugins) {
        config.plugins = [];
    }

    return config;
};
