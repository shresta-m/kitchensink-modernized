package com.kitchensink.service.port;


/** Port: counts of member changes made through the API. */
public interface MemberActivityMetrics {

    void memberCreated();

    void memberUpdated();

    void membersDeleted(int count);
}
