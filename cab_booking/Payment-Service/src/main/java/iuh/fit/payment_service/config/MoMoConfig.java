package iuh.fit.payment_service.config;

import com.mservice.config.Environment;
import com.mservice.config.MoMoEndpoint;
import com.mservice.config.PartnerInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@Slf4j
public class MoMoConfig {

    @Value("${momo.environment:dev}")
    private String environment;

    @Value("${momo.endpoint:https://test-payment.momo.vn/v2/gateway/api}")
    private String endpoint;

    @Value("${momo.partner-code:MOMOLRJZ20181206}")
    private String partnerCode;

    @Value("${momo.access-key:MTCKt9W3eU1m39TW}")
    private String accessKey;

    @Value("${momo.secret-key:SetA5RDnLHvt51AULf51DyauxUo3kDU6}")
    private String secretKey;

    @Value("${momo.environment:dev}")
    private String envTarget;

    @Bean
    @Primary
    public Environment momoEnvironment() {
        log.info("[MoMoConfig] Initializing MoMo Environment - env={}, endpoint={}", envTarget, endpoint);
        
        MoMoEndpoint momoEndpoint = new MoMoEndpoint(
                endpoint,
                "/create",
                "/refund",
                "/query",
                "/confirm",
                "/tokenization/pay",
                "/tokenization/bind",
                "/tokenization/cbQuery",
                "/tokenization/delete"
        );

        PartnerInfo partnerInfo = new PartnerInfo(partnerCode, accessKey, secretKey);
        
        Environment.EnvTarget target = "prod".equalsIgnoreCase(envTarget) 
                ? Environment.EnvTarget.PROD 
                : Environment.EnvTarget.DEV;
        
        Environment env = new Environment(momoEndpoint, partnerInfo, target);
        log.info("[MoMoConfig] MoMo Environment initialized - partnerCode={}, accessKey={}", 
                partnerCode, accessKey);
        
        return env;
    }

    @Bean
    public MoMoPropertiesExtended momoPropertiesExtended() {
        MoMoPropertiesExtended props = new MoMoPropertiesExtended();
        props.setEndpoint(endpoint);
        props.setPartnerCode(partnerCode);
        props.setAccessKey(accessKey);
        props.setSecretKey(secretKey);
        return props;
    }
}
