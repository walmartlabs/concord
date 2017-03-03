package com.walmartlabs.concord.server.ansible;

import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.project.ConfigurationValidator;
import com.walmartlabs.concord.server.security.secret.SecretDao;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.subject.Subject;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.Map;

@Named
public class AnsibleConfigurationValidator implements ConfigurationValidator {

    private final SecretDao secretDao;

    @Inject
    public AnsibleConfigurationValidator(SecretDao secretDao) {
        this.secretDao = secretDao;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void validate(Map<String, Object> m) {
        Map<String, Object> ansibleCfg = (Map<String, Object>) m.get(AnsibleConfigurationConstants.GROUP_KEY);
        if (ansibleCfg == null) {
            return;
        }

        Collection<Map<String, Object>> privateKeys = (Collection<Map<String, Object>>) ansibleCfg.get(AnsibleConfigurationConstants.PRIVATE_KEYS);
        if (privateKeys == null) {
            return;
        }

        Subject subject = SecurityUtils.getSubject();

        for (Map<String, Object> pk : privateKeys) {
            String secret = (String) pk.get(AnsibleConfigurationConstants.SECRET_KEY);
            if (secret == null) {
                continue;
            }

            if (!secretDao.exists(secret)) {
                throw new ValidationErrorsException("Secret not found: " + secret);
            }

            if (!subject.isPermitted(String.format(Permissions.SECRET_READ_INSTANCE, secret))) {
                throw new UnauthorizedException("The current user does not have permissions to use the specified secret: " + secret);
            }
        }
    }
}
