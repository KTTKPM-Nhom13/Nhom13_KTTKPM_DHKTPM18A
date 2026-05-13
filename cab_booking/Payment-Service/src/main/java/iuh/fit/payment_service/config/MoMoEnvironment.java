package iuh.fit.payment_service.config;

import lombok.Getter;
import lombok.Setter;

import java.io.InputStream;
import java.util.Properties;

@Getter
@Setter
public class MoMoEnvironment {

    private MoMoEndpoint endpoint;
    private PartnerInfo partnerInfo;
    private String target;

    public MoMoEnvironment(MoMoEndpoint endpoint, PartnerInfo partnerInfo, String target) {
        this.endpoint = endpoint;
        this.partnerInfo = partnerInfo;
        this.target = target;
    }

    public static MoMoEnvironment selectEnv(String target) {
        switch (target) {
            case "dev":
                return selectEnv(EnvTarget.DEV);
            case "prod":
                return selectEnv(EnvTarget.PROD);
            default:
                throw new IllegalArgumentException("MoMo doesnt provide other environment: dev and prod");
        }
    }

    public static MoMoEnvironment selectEnv(EnvTarget target) {
        try (InputStream input = MoMoEnvironment.class.getClassLoader().getResourceAsStream("environment.properties")) {
            Properties prop = new Properties();
            prop.load(input);

            switch (target) {
                case DEV:
                    MoMoEndpoint devEndpoint = new MoMoEndpoint(
                            prop.getProperty("DEV_MOMO_ENDPOINT"),
                            prop.getProperty("CREATE_URL"),
                            prop.getProperty("REFUND_URL"),
                            prop.getProperty("QUERY_URL"),
                            prop.getProperty("CONFIRM_URL"),
                            prop.getProperty("TOKEN_PAY_URL"),
                            prop.getProperty("TOKEN_BIND_URL"),
                            prop.getProperty("TOKEN_INQUIRY_URL"),
                            prop.getProperty("TOKEN_DELETE_URL"));
                    PartnerInfo devInfo = new PartnerInfo(
                            prop.getProperty("DEV_PARTNER_CODE"),
                            prop.getProperty("DEV_ACCESS_KEY"),
                            prop.getProperty("DEV_SECRET_KEY"));
                    return new MoMoEnvironment(devEndpoint, devInfo, target.target);
                case PROD:
                    MoMoEndpoint prodEndpoint = new MoMoEndpoint(
                            prop.getProperty("PROD_MOMO_ENDPOINT"),
                            prop.getProperty("CREATE_URL"),
                            prop.getProperty("REFUND_URL"),
                            prop.getProperty("QUERY_URL"),
                            prop.getProperty("CONFIRM_URL"),
                            prop.getProperty("TOKEN_PAY_URL"),
                            prop.getProperty("TOKEN_BIND_URL"),
                            prop.getProperty("TOKEN_INQUIRY_URL"),
                            prop.getProperty("TOKEN_DELETE_URL"));
                    PartnerInfo prodInfo = new PartnerInfo(
                            prop.getProperty("PROD_PARTNER_CODE"),
                            prop.getProperty("PROD_ACCESS_KEY"),
                            prop.getProperty("PROD_SECRET_KEY"));
                    return new MoMoEnvironment(prodEndpoint, prodInfo, target.target);
                default:
                    throw new IllegalArgumentException("MoMo doesnt provide other environment: dev and prod");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load MoMo environment configuration", e);
        }
    }

    public MoMoEndpoint getMomoEndpoint() {
        return endpoint;
    }

    public PartnerInfo getPartnerInfo() {
        return partnerInfo;
    }

    public String getTarget() {
        return target;
    }

    @Getter
    public enum EnvTarget {
        DEV("development"),
        PROD("production");

        private final String target;

        EnvTarget(String target) {
            this.target = target;
        }

        public String getTarget() {
            return this.target;
        }
    }
}
