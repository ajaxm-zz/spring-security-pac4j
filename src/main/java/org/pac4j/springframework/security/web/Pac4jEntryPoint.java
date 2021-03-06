package org.pac4j.springframework.security.web;

import org.pac4j.core.client.Client;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.context.session.JEESessionStore;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.engine.DefaultSecurityLogic;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.exception.http.HttpAction;
import org.pac4j.core.http.adapter.HttpActionAdapter;
import org.pac4j.core.http.adapter.JEEHttpActionAdapter;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.core.util.FindBest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This entry point can be defined with a security configuration and a client name:
 * if it's an indirect client, it redirects the user to te identity provider for login. Otherwise, a 401 error page is returned.
 * If no configuration is provided, an error is returned directly.
 *
 * @author Jerome Leleu
 * @since 1.0.0
 */
public class Pac4jEntryPoint extends DefaultSecurityLogic<Object, JEEContext> implements AuthenticationEntryPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(Pac4jEntryPoint.class);

    private Config config;

    private String clientName;

    public Pac4jEntryPoint() {}

    public Pac4jEntryPoint(final Config config, final String clientName) {
        this.config = config;
        this.clientName = clientName;
    }

    @Override
    public void commence(final HttpServletRequest request, final HttpServletResponse response,
                         final AuthenticationException authException) throws IOException, ServletException {

        if (config != null && CommonHelper.isNotBlank(clientName)) {
            final SessionStore<JEEContext> bestSessionStore = FindBest.sessionStore(null, config, JEESessionStore.INSTANCE);
            final HttpActionAdapter<Object, JEEContext> bestAdapter = FindBest.httpActionAdapter(null, config, JEEHttpActionAdapter.INSTANCE);
            final JEEContext context = new JEEContext(request, response, bestSessionStore);

            final List<Client<? extends Credentials>> currentClients = new ArrayList<>();
            final Client<? extends Credentials> client = config.getClients().findClient(clientName).orElseThrow(() -> new TechnicalException("Cannot find clientName: " + clientName));
            currentClients.add(client);

            HttpAction action;
            try {
                if (startAuthentication(context, currentClients)) {
                    LOGGER.debug("Redirecting to identity provider for login");
                        saveRequestedUrl(context, currentClients, config.getClients().getAjaxRequestResolver());
                        action = redirectToIdentityProvider(context, currentClients);
                } else {
                    action = unauthorized(context, currentClients);
                }
            } catch (final HttpAction e) {
                LOGGER.debug("extra HTTP action required in Pac4jEntryPoint: {}", e.getCode());
                action = e;
            }
            bestAdapter.adapt(action, context);

        } else {
            throw new TechnicalException("The Pac4jEntryPoint has been defined without config, nor clientName: it must be defined in a <security:http> section with the pac4j SecurityFilter or CallbackFilter");
        }
    }

    public Config getConfig() {
        return config;
    }

    public void setConfig(final Config config) {
        this.config = config;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(final String clientName) {
        this.clientName = clientName;
    }

    @Override
    public String toString() {
        return CommonHelper.toNiceString(this.getClass(), "config", config, "clientName", clientName);
    }
}
