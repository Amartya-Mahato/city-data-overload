package com.lemillion.city_data_overload_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * City Data Overload Server Application
 * 
 * Main application class for the intelligent backend system that serves as the
 * comprehensive city data management platform focused on Bengaluru.
 * 
 * This system provides:
 * - Real-time data ingestion from multiple sources (Twitter, Data.gov.in, OpenCity, LinkedIn)
 * - AI-powered data synthesis and analysis using Google Vertex AI
 * - Event streaming and processing via Apache Kafka
 * - Firebase integration for storage and real-time updates
 * - Location-based services optimized for Bengaluru
 * - Multimodal citizen reporting with image/video analysis
 * - Predictive alerts and pattern detection
 * - Comprehensive REST APIs for mobile and web clients
 * 
 * @author City Data Overload Team
 * @version 1.0.0
 */
@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
    org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
    org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class
})
@EnableCaching
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
public class CityDataOverloadServerApplication {

	/**
	 * Main entry point for the City Data Overload Server application.
	 * 
	 * @param args Command line arguments
	 */
	
	public static void main(String[] args) {
		SpringApplication.run(CityDataOverloadServerApplication.class, args);
	}

}