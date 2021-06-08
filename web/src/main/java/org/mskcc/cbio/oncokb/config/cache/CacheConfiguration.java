package org.mskcc.cbio.oncokb.config.cache;

import org.mskcc.cbio.oncokb.util.PropertiesUtils;
import org.mskcc.oncokb.meta.enumeration.RedisType;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.NamedCacheResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfiguration {
    @Bean
    public RedissonClient redissonClient()
        throws Exception {
        Config config = new Config();
        String redisType = PropertiesUtils.getProperties("redis.type");
        String redisPassword = PropertiesUtils.getProperties("redis.password");
        String redisAddress = PropertiesUtils.getProperties("redis.address");

        if (redisType.equals(RedisType.SINGLE.getType())) {
            config
                .useSingleServer()
                .setAddress(redisAddress)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(2)
                .setPassword(redisPassword);
        } else if (redisType.equals(RedisType.SENTINEL.getType())) {
            config
                .useSentinelServers()
                .setMasterName("oncokb-master")
                .setCheckSentinelsList(false)
                .addSentinelAddress(redisAddress)
                .setPassword(redisPassword);
        } else {
            throw new Exception(
                "The redis type " +
                    redisType +
                    " is not supported. Only single and sentinel are supported."
            );
        }
        return Redisson.create(config);
    }

    @Bean
    public org.springframework.cache.CacheManager cacheManager(
        RedissonClient redissonClient) {
        CacheManager cm = new CustomRedisCacheManager(redissonClient, 60);
        return cm;
    }

    @Bean
    public CacheResolver geneCacheResolver(
        CacheManager cm,
        CacheNameResolver cacheNameResolver
    ) {
        return new GeneCacheResolver(cm, cacheNameResolver);
    }
}
