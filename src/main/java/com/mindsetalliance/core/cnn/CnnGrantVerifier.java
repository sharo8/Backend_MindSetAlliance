package com.mindsetalliance.core.cnn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Au démarrage, SHOW GRANTS. Un droit autre que SELECT, SHOW VIEW ou USAGE
 * laisse readOnlyVerified à false. Aucun identifiant n'est journalisé.
 */
@Component
@ConditionalOnProperty(name = "ma.cnn.mode", havingValue = "db")
@Conditional(CnnDbUrlCondition.class)
public class CnnGrantVerifier implements ApplicationRunner {

    static final Set<String> ALLOWED = Set.of("SELECT", "SHOW VIEW", "USAGE");

    private static final Logger log = LoggerFactory.getLogger(CnnGrantVerifier.class);

    private final CnnReadOnlyClient client;
    private final CnnSourceStatus status;

    public CnnGrantVerifier(@Qualifier("cnnJdbcTemplate") JdbcTemplate jdbc, CnnSourceStatus status) {
        this.client = new CnnReadOnlyClient(jdbc);
        this.status = status;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<String> grants = new ArrayList<>();
            client.query("SHOW GRANTS FOR CURRENT_USER()", rs -> {
                grants.add(rs.getString(1));
            });
            List<String> extra = new ArrayList<>();
            for (String grant : grants) {
                extra.addAll(extraPrivileges(grant));
            }
            if (extra.isEmpty() && !grants.isEmpty()) {
                status.mark(true, true);
                log.info("Source Colis na Nga : droits limités à la lecture (readOnlyVerified=true)");
                return;
            }
            status.mark(true, false);
            log.warn("Source Colis na Nga : droit autre que SELECT/SHOW VIEW/USAGE ({}) — readOnlyVerified=false",
                    extra.isEmpty() ? "aucun GRANT lu" : String.join(", ", extra));
        } catch (RuntimeException ex) {
            status.mark(false, false);
            log.warn("Source Colis na Nga injoignable : vérification des droits non faite (readOnlyVerified=false)");
        }
    }

    static List<String> extraPrivileges(String grant) {
        if (grant == null) {
            return List.of("INCONNU");
        }
        String upper = grant.toUpperCase(Locale.ROOT);
        int grantAt = upper.indexOf("GRANT ");
        int on = upper.indexOf(" ON ");
        if (grantAt < 0 || on < 0 || on <= grantAt) {
            return List.of("INCONNU");
        }
        String privs = upper.substring(grantAt + "GRANT ".length(), on);
        List<String> extra = new ArrayList<>();
        for (String part : privs.split(",")) {
            String privilege = CnnSql.privilege(part);
            if (privilege.isEmpty() || ALLOWED.contains(privilege)) {
                continue;
            }
            extra.add(privilege);
        }
        return extra;
    }
}
