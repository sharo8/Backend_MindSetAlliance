package com.mindsetalliance.core.cnn;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Vrai seulement si l'URL JDBC Colis na Nga est renseignée et non vide. */
class CnnDbUrlCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String url = context.getEnvironment().getProperty("ma.cnn.db.url");
        return url != null && !url.isBlank();
    }
}
