package com.withwon.domain.checkitem;

public interface CheckItemConfigProjection {

    String getItemCode();

    String getQuestion();

    String getAnswerType();

    String getAnswerOptions();

    Boolean getRequired();

    String getScope();
}
