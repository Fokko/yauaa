/*
 * Yet Another UserAgent Analyzer
 * Copyright (C) 2013-2018 Niels Basjes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nl.basjes.parse.useragent.nifi;

import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@SideEffectFree
@Tags({"UserAgent", "Web Analytics"})
@CapabilityDescription("Extract attributes from the UserAgent string.")
public class ParseUserAgentProcessor extends AbstractProcessor {

    public static final Relationship SUCCESS = new Relationship.Builder()
        .name("success")
        .description("Success Relationship")
        .build();

    private Set<Relationship> relationships;

    private UserAgentAnalyzer uaa;

    private List<String> allFieldNames;

    @Override
    protected void init(ProcessorInitializationContext context) {
        super.init(context);

        uaa = UserAgentAnalyzer
            .newBuilder()
            .withField("AgentNameVersion")
            .hideMatcherLoadStats()
            .dropTests()
            .build();

        allFieldNames = uaa.getAllPossibleFieldNamesSorted();

        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(SUCCESS);
        this.relationships = Collections.unmodifiableSet(relationships);
    }


    @Override
    public Set<Relationship> getRelationships() {
        return this.relationships;
    }

    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {

        final AtomicReference<FlowFile> flowFile = new AtomicReference<>(session.get());

        session.read(flowFile.get(), in -> {
            try{
                String userAgentString = IOUtils.toString(in, StandardCharsets.UTF_8);
                UserAgent userAgent = uaa.parse(userAgentString);

                for (String fieldName: allFieldNames) {
                    String fieldValue = userAgent.getValue(fieldName);
                    session.putAttribute(flowFile.get(), fieldName, fieldValue);
                }
            } catch(Exception ex){
                ex.printStackTrace();
                getLogger().error("Failed to parse the useragent.");
            }
        });

        session.transfer(flowFile.get(), SUCCESS);
        session.commit();
    }

}
