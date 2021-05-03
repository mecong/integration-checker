package mecong;

import com.github.javaparser.JavaParser;
import com.mecong.maven.plugin.annotations.IntegrationDtoExpose;

@IntegrationDtoExpose
public class SimpleProducerPojo {
    Integer age;
    String name;
    double newField;
    JavaParser parser = new JavaParser();
}
