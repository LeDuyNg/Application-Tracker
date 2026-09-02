package dev.duynguyen.jobtracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import dev.duynguyen.jobtracker.common.AppProperties;

/**
 * The door policy, unit-tested.
 *
 * <p>This is the whole multi-user story for a deliberately single-user app: anyone on the
 * internet can start a Google login, and this class decides which of them becomes a session.
 * Every case here is a rejection except one, which is the right ratio for an allowlist.
 */
class AllowlistOidcUserServiceTest {

    private AllowlistOidcUserService serviceAllowing(String... emails) {
        AppProperties properties = new AppProperties();
        properties.setAllowedEmails(List.of(emails));
        return new AllowlistOidcUserService(properties);
    }

    /** A Google login result, without going near Google. */
    private OidcUser googleUser(String email, Boolean emailVerified) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "1234567890");
        if (email != null) {
            claims.put("email", email);
        }
        if (emailVerified != null) {
            claims.put("email_verified", emailVerified);
        }
        OidcIdToken idToken = new OidcIdToken(
                "id-token", Instant.now(), Instant.now().plusSeconds(3600), claims);
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
    }

    @Test
    @DisplayName("the allowlisted address is let in")
    void allowlistedEmailPasses() {
        OidcUser user = googleUser("owner@example.com", true);

        assertThatCode(() -> serviceAllowing("owner@example.com").verify(user))
                .doesNotThrowAnyException();
        assertThat(serviceAllowing("owner@example.com").verify(user).getEmail())
                .isEqualTo("owner@example.com");
    }

    @Test
    @DisplayName("any other address is refused")
    void otherEmailRejected() {
        assertThatThrownBy(() -> serviceAllowing("owner@example.com")
                .verify(googleUser("someone.else@example.com", true)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("not permitted");
    }

    @Test
    @DisplayName("an unverified email is refused even when it is the allowlisted address")
    void unverifiedEmailRejected() {
        // The case the check exists for. Without it, anyone who could get Google to issue a
        // token claiming this address would be let in — the claim would be a string the
        // account holder typed, not something Google vouches for.
        assertThatThrownBy(() -> serviceAllowing("owner@example.com")
                .verify(googleUser("owner@example.com", false)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("not verified");
    }

    @Test
    @DisplayName("a missing email_verified claim is treated as unverified, not as absent")
    void missingVerifiedClaimRejected() {
        // Boolean.TRUE.equals(null) is false, so a claim Google simply did not send fails
        // closed. Written down because `if (!verified)` on a Boolean would NPE here instead,
        // and a null-tolerant `if (verified == Boolean.FALSE)` would let it through.
        assertThatThrownBy(() -> serviceAllowing("owner@example.com")
                .verify(googleUser("owner@example.com", null)))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    @DisplayName("comparison ignores case and surrounding whitespace")
    void emailComparisonIsNormalized() {
        // Google returns the address in whatever case the account uses; the allowlist is
        // typed by a human into a config file. Neither side should have to be careful.
        assertThatCode(() -> serviceAllowing("  Owner@Example.com  ")
                .verify(googleUser("owner@example.com", true)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an empty allowlist admits nobody")
    void emptyAllowlistFailsClosed() {
        // An allowlist that fails open is not an allowlist. A deploy that forgets
        // APP_ALLOWED_EMAILS locks the owner out — recoverable; the alternative admits the
        // internet.
        assertThatThrownBy(() -> serviceAllowing().verify(googleUser("owner@example.com", true)))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }
}
