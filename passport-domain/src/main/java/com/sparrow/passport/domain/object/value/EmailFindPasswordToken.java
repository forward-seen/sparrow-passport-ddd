package com.sparrow.passport.domain.object.value;

import com.sparrow.constant.Config;
import com.sparrow.constant.ConfigKeyLanguage;
import com.sparrow.constant.SparrowError;
import com.sparrow.enums.DateTimeUnit;
import com.sparrow.exception.Asserts;
import com.sparrow.protocol.BusinessException;
import com.sparrow.protocol.ddd.ValueObject;
import com.sparrow.passport.domain.DomainRegistry;
import com.sparrow.passport.domain.service.EncryptionService;
import com.sparrow.utility.ConfigUtility;
import com.sparrow.utility.DateTimeUtility;
import java.sql.Timestamp;
import java.util.Objects;

public class EmailFindPasswordToken implements ValueObject<EmailFindPasswordToken> {
    private DomainRegistry domainRegistry;

    private static final String TOKEN_TYPE = "find-password";

    public static EmailFindPasswordToken createToken(Long userId, String userName, String email, String password,
        String currentDate,
        DomainRegistry domainRegistry) {
        EmailFindPasswordToken emailPasswordToken = new EmailFindPasswordToken();
        emailPasswordToken.userId = userId;
        emailPasswordToken.userName = userName;
        emailPasswordToken.sendDate = currentDate;
        emailPasswordToken.email = email;
        emailPasswordToken.domainRegistry = domainRegistry;
        emailPasswordToken.password = password;
        return emailPasswordToken;
    }

    public static EmailFindPasswordToken parse(EmailTokenPair emailTokenPair, String password,
        DomainRegistry domainRegistry) {
        if (emailTokenPair == null) {
            return null;
        }
        EncryptionService encryptionService = domainRegistry.getEncryptionService();
        EmailFindPasswordToken findPasswordOriginToken = new EmailFindPasswordToken();
        findPasswordOriginToken.email = emailTokenPair.getEmail();

        String originToken = encryptionService.decryptToken(emailTokenPair.getToken(), password);
        String[] array = originToken.split("\\|");
        findPasswordOriginToken.tokenType = array[0];
        findPasswordOriginToken.userId = Long.parseLong(array[1]);
        findPasswordOriginToken.userName = array[2];
        findPasswordOriginToken.sendDate = array[3];
        findPasswordOriginToken.password = password;

        return findPasswordOriginToken;
    }

    private Long userId;
    private String userName;
    private String sendDate;
    private String email;
    private String password;
    private String tokenType;

    private String generateOriginToken() {
        // 令牌原码
        return TOKEN_TYPE + "|" + this.userId + "|" + this.userName + "|" + this.sendDate;
    }

    private String generateToken() {
        EncryptionService encryptionService = domainRegistry.getEncryptionService();
        String encryptedToken = encryptionService.generateToken(this.generateOriginToken(), this.password);
        return encryptionService.base64Encode(EmailTokenPair.create(this.email, encryptedToken).toString());
    }

    public String generateContent() {
        String language = ConfigUtility.getValue(Config.LANGUAGE);
        return ConfigUtility
            .getLanguageValue(ConfigKeyLanguage.PASSWORD_EMAIL_CONTENT,
                language)
            .replace("$rootPath", ConfigUtility.getValue(Config.ROOT_PATH))
            .replace("$validateToken", this.generateToken())
            .replace("$date", this.sendDate);
    }

    public boolean isValid(String originUserName) throws BusinessException {
        Asserts.isTrue(!this.userName.equals(originUserName), SparrowError.USER_PASSWORD_VALIDATE_TOKEN_ERROR);
        Asserts.isTrue(!TOKEN_TYPE.equals(this.tokenType), SparrowError.USER_TOKEN_TYPE_ERROR);

        int validateTokenAvailableDay = Integer.parseInt(ConfigUtility
            .getValue(Config.VALIDATE_TOKEN_AVAILABLE_DAY));
        int passDay = DateTimeUtility.getInterval(
            Timestamp.valueOf(sendDate).getTime(),
            System.currentTimeMillis(),
            DateTimeUnit.DAY);
        if (passDay > validateTokenAvailableDay) {
            throw new BusinessException(SparrowError.USER_VALIDATE_TOKEN_TIME_OUT);
        }
        return true;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof EmailFindPasswordToken))
            return false;
        EmailFindPasswordToken token = (EmailFindPasswordToken) o;
        return
            userId.equals(token.userId) &&
                userName.equals(token.userName) &&
                sendDate.equals(token.sendDate) &&
                email.equals(token.email) &&
                password.equals(token.password);
    }

    @Override public int hashCode() {
        return Objects.hash(userId, userName, sendDate, email, password);
    }

    @Override public boolean sameValueAs(EmailFindPasswordToken token) {
        if (this.hashCode() != token.hashCode()) {
            return false;
        }
        return this.equals(token);
    }
}
