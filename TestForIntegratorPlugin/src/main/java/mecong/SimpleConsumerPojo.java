package mecong;

import com.github.javaparser.JavaParser;
import com.mecong.maven.plugin.annotations.IntegrationDtoBound;

@IntegrationDtoBound("TestForIntegratorPlugin-mecong.SimpleProducerPojo")
public class SimpleConsumerPojo {
    Integer age;
    double newField;
    String name;
    JavaParser parser = new JavaParser();
}
