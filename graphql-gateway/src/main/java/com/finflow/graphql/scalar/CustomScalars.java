package com.finflow.graphql.scalar;

import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomScalars {

    @Bean
    public GraphQlSourceBuilderCustomizer customScalarsCustomizer() {
        return builder ->
                builder.configureRuntimeWiring(
                        wiringBuilder ->
                                wiringBuilder.scalar(dateScalar())
                                        .scalar(dateTimeScalar())
                                        .scalar(bigDecimalScalar()));
    }

    @Bean
    public GraphQLScalarType dateScalar() {
        return GraphQLScalarType.newScalar()
                .name("Date")
                .description("java.time.LocalDate scalar -- format: yyyy-MM-dd")
                .coercing(
                        new Coercing<LocalDate, String>() {
                            @Override
                            public String serialize(Object dataFetcherResult) {
                                try {
                                    if (dataFetcherResult == null) {
                                        return null;
                                    }
                                    if (dataFetcherResult instanceof LocalDate localDate) {
                                        return localDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
                                    }
                                    if (dataFetcherResult instanceof String text) {
                                        return LocalDate.parse(text).format(DateTimeFormatter.ISO_LOCAL_DATE);
                                    }
                                    throw new CoercingSerializeException(
                                            "Expected LocalDate or String for Date scalar");
                                } catch (CoercingSerializeException ex) {
                                    throw ex;
                                } catch (Exception ex) {
                                    throw new CoercingSerializeException("Invalid Date value", ex);
                                }
                            }

                            @Override
                            public LocalDate parseValue(Object input) {
                                try {
                                    if (input == null) {
                                        return null;
                                    }
                                    if (input instanceof LocalDate localDate) {
                                        return localDate;
                                    }
                                    if (input instanceof String text) {
                                        return LocalDate.parse(text);
                                    }
                                    throw new CoercingParseValueException(
                                            "Expected String input for Date scalar");
                                } catch (CoercingParseValueException ex) {
                                    throw ex;
                                } catch (Exception ex) {
                                    throw new CoercingParseValueException("Invalid Date input", ex);
                                }
                            }

                            @Override
                            public LocalDate parseLiteral(Object input) {
                                try {
                                    if (input == null) {
                                        return null;
                                    }
                                    if (input instanceof StringValue value) {
                                        return LocalDate.parse(value.getValue());
                                    }
                                    throw new CoercingParseLiteralException(
                                            "Expected StringValue literal for Date scalar");
                                } catch (CoercingParseLiteralException ex) {
                                    throw ex;
                                } catch (Exception ex) {
                                    throw new CoercingParseLiteralException("Invalid Date literal", ex);
                                }
                            }
                        })
                .build();
    }

    @Bean
    public GraphQLScalarType dateTimeScalar() {
        return GraphQLScalarType.newScalar()
                .name("DateTime")
                .description("java.time.Instant scalar -- ISO 8601 UTC format")
                .coercing(
                        new Coercing<Instant, String>() {
                            @Override
                            public String serialize(Object dataFetcherResult) {
                                try {
                                    if (dataFetcherResult == null) {
                                        return null;
                                    }
                                    if (dataFetcherResult instanceof Instant instant) {
                                        return DateTimeFormatter.ISO_INSTANT.format(instant);
                                    }
                                    if (dataFetcherResult instanceof String text) {
                                        return DateTimeFormatter.ISO_INSTANT.format(Instant.parse(text));
                                    }
                                    throw new CoercingSerializeException(
                                            "Expected Instant or String for DateTime scalar");
                                } catch (CoercingSerializeException ex) {
                                    throw ex;
                                } catch (Exception ex) {
                                    throw new CoercingSerializeException("Invalid DateTime value", ex);
                                }
                            }

                            @Override
                            public Instant parseValue(Object input) {
                                try {
                                    if (input == null) {
                                        return null;
                                    }
                                    if (input instanceof Instant instant) {
                                        return instant;
                                    }
                                    if (input instanceof String text) {
                                        return Instant.parse(text);
                                    }
                                    throw new CoercingParseValueException(
                                            "Expected String input for DateTime scalar");
                                } catch (CoercingParseValueException ex) {
                                    throw ex;
                                } catch (Exception ex) {
                                    throw new CoercingParseValueException("Invalid DateTime input", ex);
                                }
                            }

                            @Override
                            public Instant parseLiteral(Object input) {
                                try {
                                    if (input == null) {
                                        return null;
                                    }
                                    if (input instanceof StringValue value) {
                                        return Instant.parse(value.getValue());
                                    }
                                    throw new CoercingParseLiteralException(
                                            "Expected StringValue literal for DateTime scalar");
                                } catch (CoercingParseLiteralException ex) {
                                    throw ex;
                                } catch (Exception ex) {
                                    throw new CoercingParseLiteralException("Invalid DateTime literal", ex);
                                }
                            }
                        })
                .build();
    }

    @Bean
    public GraphQLScalarType bigDecimalScalar() {
        return GraphQLScalarType.newScalar()
                .name("BigDecimal")
                .description("java.math.BigDecimal for precise monetary values")
                .coercing(
                        new Coercing<BigDecimal, Object>() {
                            @Override
                            public Object serialize(Object dataFetcherResult) {
                                try {
                                    if (dataFetcherResult == null) {
                                        return null;
                                    }
                                    if (dataFetcherResult instanceof BigDecimal bigDecimal) {
                                        return bigDecimal;
                                    }
                                    if (dataFetcherResult instanceof String text) {
                                        return new BigDecimal(text);
                                    }
                                    if (dataFetcherResult instanceof Number number) {
                                        return BigDecimal.valueOf(number.doubleValue());
                                    }
                                    throw new CoercingSerializeException(
                                            "Expected BigDecimal, String, or Number for BigDecimal scalar");
                                } catch (CoercingSerializeException ex) {
                                    throw ex;
                                } catch (Exception ex) {
                                    throw new CoercingSerializeException("Invalid BigDecimal value", ex);
                                }
                            }

                            @Override
                            public BigDecimal parseValue(Object input) {
                                try {
                                    if (input == null) {
                                        return null;
                                    }
                                    if (input instanceof BigDecimal bigDecimal) {
                                        return bigDecimal;
                                    }
                                    if (input instanceof String text) {
                                        return new BigDecimal(text);
                                    }
                                    if (input instanceof Number number) {
                                        return BigDecimal.valueOf(number.doubleValue());
                                    }
                                    throw new CoercingParseValueException(
                                            "Expected BigDecimal, String, or Number input");
                                } catch (CoercingParseValueException ex) {
                                    throw ex;
                                } catch (Exception ex) {
                                    throw new CoercingParseValueException("Invalid BigDecimal input", ex);
                                }
                            }

                            @Override
                            public BigDecimal parseLiteral(Object input) {
                                try {
                                    if (input == null) {
                                        return null;
                                    }
                                    if (input instanceof StringValue value) {
                                        return new BigDecimal(value.getValue());
                                    }
                                    if (input instanceof IntValue value) {
                                        return new BigDecimal(value.getValue());
                                    }
                                    if (input instanceof FloatValue value) {
                                        return value.getValue();
                                    }
                                    throw new CoercingParseLiteralException(
                                            "Expected StringValue, IntValue, or FloatValue literal");
                                } catch (CoercingParseLiteralException ex) {
                                    throw ex;
                                } catch (Exception ex) {
                                    throw new CoercingParseLiteralException("Invalid BigDecimal literal", ex);
                                }
                            }
                        })
                .build();
    }
}
