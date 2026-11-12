/*
 * Entry point of the graph pipeline. Diagram data arrives through the host
 * (never as script text), so this script stays constant and cacheable.
 */
(function () {
    'use strict';
    var request = JSON.parse(String(drawioHost.loadResource('request')));
    request.model = String(drawioHost.loadResource('model'));
    request.stylesheet = String(drawioHost.loadResource('stylesheet'));
    return DrawioHeadless.render(request);
})();
