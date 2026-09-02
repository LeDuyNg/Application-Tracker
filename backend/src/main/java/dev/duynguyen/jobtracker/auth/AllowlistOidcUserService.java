package dev.duynguyen.jobtracker.auth;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;

import dev.duynguyen.jobtracker.common.AppProperties;

/**
 * Lets exactly one Google account in.
 *
 * <p>This is the entire multi-user story: the app is single-user by design (CLAUDE.md §14),
 * and rather than roles and ownership checks on every document, one allowlist decides at the
 * door. Anyone can start a Google login — that is Google's page, not ours — so this is the
 * point where "authenticated by Google" becomes "allowed to use this app".
 *
 * <p><strong>{@code email_verified} is checked as well as the allowlist.</strong> An
 * unverified email claim is a string the account holder typed, not an identity Google
 * vouches for; without this check, anyone able to create a Google account claiming the
 * allowlisted address would pass. Google does not normally issue unverified emails for
 * consumer accounts, which is exactly why the check is cheap and why forgetting it goes
 * unnoticed.
 *
 * <p>Named for what it is — an {@code OidcUserService}, because Google's {@code openid}
 * scope makes this an OIDC login rather than plain OAuth2. CLAUDE.md §5 originally planned
 * it as {@code AllowlistOAuth2UserService}; that layout entry has been corrected.
 */
@Service
public class AllowlistOidcUserService extends OidcUserService {

    private static final Logger log = LoggerFactory.getLogger(AllowlistOidcUserService.class);

    private final AppProperties properties;

    AllowlistOidcUserService(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        return verify(super.loadUser(request));
    }

    /**
     * The allowlist decision, separated from the network call that fetches the user.
     *
     * <p>{@code loadUser} cannot be unit-tested — {@code super.loadUser} calls Google's
     * userinfo endpoint. Splitting the rule out means the part with the actual logic in it
     * is testable with a hand-built {@link OidcUser} and no network, which is the only way
     * "an unverified email is rejected" gets an assertion rather than a comment.
     */
    OidcUser verify(OidcUser user) {
        String email = user.getEmail();
        Boolean verified = user.getEmailVerified();

        if (email == null || !Boolean.TRUE.equals(verified)) {
            log.warn("rejected login: email missing or unverified");
            throw denied("Your Google account's email address is not verified.");
        }
        if (!properties.getAllowedEmails().contains(email.toLowerCase(Locale.ROOT))) {
            // Logged, because a rejected login on a single-user app is worth noticing. The
            // address is logged; the token never is.
            log.warn("rejected login for non-allowlisted address: {}", email);
            throw denied("This account is not permitted to use this application.");
        }
        return user;
    }

    private OAuth2AuthenticationException denied(String description) {
        return new OAuth2AuthenticationException(
                new OAuth2Error("access_denied", description, null), description);
    }
}
