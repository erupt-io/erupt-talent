package xyz.erupt.talent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import xyz.erupt.core.annotation.EruptScan;

/**
 * Full-lifecycle talent management system built on Erupt.
 *
 * @author YuePeng
 */
@EruptScan
@EntityScan
@SpringBootApplication
public class TalentApplication {

    public static void main(String[] args) {
        SpringApplication.run(TalentApplication.class, args);
    }

}
