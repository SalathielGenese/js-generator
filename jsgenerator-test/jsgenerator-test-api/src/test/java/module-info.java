module com.osscameroon.jsgenerator.test.api {
    requires com.osscameroon.jsgenerator.api;
    requires com.osscameroon.jsgenerator.core;

    requires com.fasterxml.jackson.databind;
    requires org.hamcrest;
    requires org.junit.jupiter.params;
    requires spring.beans;
    requires spring.boot.test;
    requires spring.security.test;
    requires spring.test;
    requires spring.web;

    requires org.assertj.core;
    requires org.junit.jupiter.api;
}