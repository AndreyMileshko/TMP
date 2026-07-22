package com.tmp.capability.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tmp.document.api.DocumentOperationContext;
import com.tmp.document.api.DocumentProcessor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IntegrationContributionDescriptorsTest {

    private static DocumentProcessor processorFor(String documentTypeId) {
        return new DocumentProcessor() {
            @Override
            public String documentTypeId() {
                return documentTypeId;
            }

            @Override
            public void validateCreate(DocumentOperationContext context) {
                // test double: no-op
            }

            @Override
            public void validateUpdate(DocumentOperationContext context) {
                // test double: no-op
            }

            @Override
            public void onPost(DocumentOperationContext context) {
                // test double: no-op
            }

            @Override
            public void onUnpost(DocumentOperationContext context) {
                // test double: no-op
            }

            @Override
            public void onClose(DocumentOperationContext context) {
                // test double: no-op
            }

            @Override
            public void onDelete(DocumentOperationContext context) {
                // test double: no-op
            }
        };
    }

    interface SampleService {
        String greet();
    }

    static final class SampleServiceImpl implements SampleService {
        @Override
        public String greet() {
            return "hello";
        }
    }

    @Nested
    class PublicServiceContributionTests {

        @Test
        void validConstructionExposesGivenValues() {
            SampleServiceImpl instance = new SampleServiceImpl();

            PublicServiceContribution<SampleService> contribution =
                    PublicServiceContribution.of(SampleService.class, instance);

            assertEquals(SampleService.class, contribution.serviceType());
            assertSame(instance, contribution.serviceInstance());
        }

        @Test
        @SuppressWarnings("unchecked")
        void typeMismatchRejected() {
            Class<Object> declaredType = (Class<Object>) (Class<?>) Runnable.class;
            Object mismatchedInstance = new SampleServiceImpl();

            assertThrows(
                    IllegalArgumentException.class,
                    () -> PublicServiceContribution.of(declaredType, mismatchedInstance));
        }

        @Test
        void nullServiceTypeRejected() {
            assertThrows(
                    NullPointerException.class,
                    () -> PublicServiceContribution.of(null, new SampleServiceImpl()));
        }

        @Test
        void nullServiceInstanceRejected() {
            assertThrows(
                    NullPointerException.class,
                    () -> PublicServiceContribution.of(SampleService.class, null));
        }

        @Test
        void equalsAndHashCodeByServiceType() {
            PublicServiceContribution<SampleService> first =
                    PublicServiceContribution.of(SampleService.class, new SampleServiceImpl());
            PublicServiceContribution<SampleService> second =
                    PublicServiceContribution.of(SampleService.class, new SampleServiceImpl());

            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
        }
    }

    @Nested
    class EventContributionTests {

        @Test
        void validConstructionExposesGivenValues() {
            EventContribution contribution = EventContribution.of("sample.event.created", "Raised on creation");

            assertEquals("sample.event.created", contribution.eventTypeId());
            assertEquals("Raised on creation", contribution.description());
        }

        @Test
        void blankEventTypeIdRejected() {
            assertThrows(IllegalArgumentException.class, () -> EventContribution.of("  ", "desc"));
        }

        @Test
        void nullEventTypeIdRejected() {
            assertThrows(NullPointerException.class, () -> EventContribution.of(null, "desc"));
        }

        @Test
        void equalsAndHashCodeByEventTypeId() {
            EventContribution first = EventContribution.of("sample.event", "A");
            EventContribution second = EventContribution.of("sample.event", "B");
            EventContribution different = EventContribution.of("other.event", "A");

            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
            assertNotEquals(first, different);
        }
    }

    @Nested
    class SettingsContributionTests {

        @Test
        void validConstructionExposesGivenValues() {
            SettingsContribution contribution =
                    SettingsContribution.of("sample.setting", "Sample setting", "A sample setting", "42");

            assertEquals("sample.setting", contribution.settingKey());
            assertEquals("Sample setting", contribution.displayName());
            assertEquals("A sample setting", contribution.description());
            assertEquals("42", contribution.defaultValue());
        }

        @Test
        void blankSettingKeyRejected() {
            assertThrows(
                    IllegalArgumentException.class, () -> SettingsContribution.of("", "Sample", "desc", "0"));
        }

        @Test
        void nullSettingKeyRejected() {
            assertThrows(
                    NullPointerException.class, () -> SettingsContribution.of(null, "Sample", "desc", "0"));
        }

        @Test
        void equalsAndHashCodeBySettingKey() {
            SettingsContribution first = SettingsContribution.of("sample.setting", "A", "desc-a", "0");
            SettingsContribution second = SettingsContribution.of("sample.setting", "B", "desc-b", "1");
            SettingsContribution different = SettingsContribution.of("other.setting", "A", "desc-a", "0");

            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
            assertNotEquals(first, different);
        }
    }

    @Nested
    class DocumentContributionTests {

        @Test
        void validConstructionExposesGivenValues() {
            DocumentProcessor processor = processorFor("sample.document");

            DocumentContribution contribution =
                    DocumentContribution.of("sample.document", "Sample document", "A sample document", processor);

            assertEquals("sample.document", contribution.documentTypeId());
            assertEquals("Sample document", contribution.displayName());
            assertEquals("A sample document", contribution.description());
            assertSame(processor, contribution.processor());
        }

        @Test
        void documentTypeIdMismatchRejected() {
            DocumentProcessor processor = processorFor("other.document");

            assertThrows(
                    IllegalArgumentException.class,
                    () -> DocumentContribution.of("sample.document", "Sample document", "desc", processor));
        }

        @Test
        void blankDocumentTypeIdRejected() {
            DocumentProcessor processor = processorFor("  ");

            assertThrows(
                    IllegalArgumentException.class,
                    () -> DocumentContribution.of("  ", "Sample document", "desc", processor));
        }

        @Test
        void nullDocumentTypeIdRejected() {
            DocumentProcessor processor = processorFor("sample.document");

            assertThrows(
                    NullPointerException.class,
                    () -> DocumentContribution.of(null, "Sample document", "desc", processor));
        }

        @Test
        void nullProcessorRejected() {
            assertThrows(
                    NullPointerException.class,
                    () -> DocumentContribution.of("sample.document", "Sample document", "desc", null));
        }

        @Test
        void equalsAndHashCodeByDocumentTypeId() {
            DocumentContribution first =
                    DocumentContribution.of("sample.document", "A", "desc-a", processorFor("sample.document"));
            DocumentContribution second =
                    DocumentContribution.of("sample.document", "B", "desc-b", processorFor("sample.document"));
            DocumentContribution different =
                    DocumentContribution.of("other.document", "A", "desc-a", processorFor("other.document"));

            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
            assertNotEquals(first, different);
        }
    }
}
