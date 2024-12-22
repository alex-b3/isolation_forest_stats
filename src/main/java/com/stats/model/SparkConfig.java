package com.stats.model;

import org.apache.spark.SparkConf;
import org.apache.spark.serializer.KryoSerializer;
import org.apache.spark.sql.SparkSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SparkConfig {


    @Bean
    public SparkSession sparkSession() {

        SparkConf conf = new SparkConf()
                .setAppName("MyApp")
                .setMaster("local")
                .set("spark.serializer", KryoSerializer.class.getName())
                .set("spark.sql.warehouse.dir", "file://///Users/alex.bichovsky/Desktop/Isolation_forest_stats_poc/src/main/resources")
                .set("spark.driver.bindAddress", "127.0.0.1")
                .registerKryoClasses(new Class<?>[]{ActivityDataExtended.class});

        return SparkSession.builder()
                .appName("Find Stats Anomaly")
                .config(conf)

//                .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
//                .config("spark.kryo.classesToRegister",
//                        "com.myheritage.model.ActivityDataExtended," +
//                                "com.myheritage.model.ActivityCompositeKey," +
//                                "com.myheritage.model.ActivityData")
//                .config("spark.kryo.referenceTracking", "true")
//                .config("spark.kryo.registrationRequired","false")
                .master("local[*]").getOrCreate();
    }
}
