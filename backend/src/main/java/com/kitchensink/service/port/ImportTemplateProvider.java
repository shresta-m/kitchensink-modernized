package com.kitchensink.service.port;


/** Port: produces the downloadable import template. */
public interface ImportTemplateProvider {

    byte[] template();
}
