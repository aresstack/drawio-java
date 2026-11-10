/*
 * Provide an inert window object only for mxEvent.js initialization.
 * Do not add document, browser events, networking or HTML layout here.
 * Any attempted DOM operation must remain unsupported and fail explicitly.
 */
var window = Object.freeze(Object.create(null));

mxUtils.bind = function (scope, callback) {
    return function () {
        return callback.apply(scope, arguments);
    };
};

mxUtils.isNumeric = function (value) {
    return !isNaN(parseFloat(value)) && isFinite(value)
        && (typeof value !== 'string' || value.toLowerCase().indexOf('0x') < 0);
};
