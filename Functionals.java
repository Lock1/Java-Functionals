import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;



/**
 * Java functional programming utilities.
 * Designed to be zero external dependency.
 */
public enum Functionals { ; // Namespace language construct via empty-enum
    // Very pointless and method-reference oriented. Functionals.pipe(Map.Entry::getKey, Model::data1);
    public static <T1,T2,Result> Function<T1,Result> pipe(Function<T1,? extends T2> f1, Function<? super T2,Result> f2) {
        return f1.andThen(f2);
    }

    /** See {@link #pipe(Function,Function)}. {@code Functionals.pipe(Model::dataList, Service::filter, Finisher::fold)} */
    public static <T1,T2,T3,Result> Function<T1,Result> pipe(
        Function<T1,? extends T2> f1,
        Function<? super T2,? extends T3> f2,
        Function<? super T3,Result> f3
    ) {
        return f1.andThen(f2).andThen(f3);
    }

    public static FunctionalInterface.Internal.Castable<?> cast(Object obj) {
        @SuppressWarnings({"rawtypes"})
        FunctionalInterface.Internal.Castable<?> result = 
                (FunctionalInterface.Internal.Castable) clazz -> clazz.isInstance(obj) ? Optional.of(clazz.cast(obj)) : Optional.empty();
        return result;
    }

    public enum Data { ;
        public static record Tuple2<T1,T2>(T1 v1, T2 v2) {}
        // Budget pre-record edition
        public static final class TupleOf2<T1,T2> {
            public final T1 v1;
            public final T2 v2;

            public TupleOf2(T1 v1, T2 v2) {
                this.v1 = v1;
                this.v2 = v2;
            }
        }
    }

    // Functional interfaces
    public enum FunctionalInterface { ;
        /** N-ary generalization of {@link java.util.function.Function} */
        public interface FunctionOf2<T1,T2,Result> { Result apply(T1 t1, T2 t2); }
        public interface FunctionOf3<T1,T2,T3,Result> { Result apply(T1 t1, T2 t2, T3 t3); }

        /** N-ary generalization of {@link java.util.function.Consumer} */
        public interface ConsumerOf2<T1,T2> { void accept(T1 t1, T2 t2); }

        private enum Internal { ;
            /** 
              * Turns out my {@link Optionals.Internal.IntermediateOptionalArgsOf2} abuses raw-types to work.
              * I had to introduce phantom type for this to work with lambdas
              */
            public interface Castable<Phantom> { <TargetType> Optional<TargetType> to(Class<TargetType> clazz); }
        }
    }




    /** ----- Namespace section ----- */
    // Predicate utilities primarily designed for filter()
    public enum Filter { ;
        public static <T,Key> Predicate<T> keyEquals(Function<? super T,Key> keyFunction, Key value) {
            return v -> Objects.equals(value, keyFunction.apply(v));
        }
    }

    // Optional utilities
    public enum Optionals { ;
        // Functionals.applyArgs(Optional.of(10), Optional.of("hello")).toFunction((number, str) -> str + Integer.toString(number))
        public static <T1,T2> Internal.IntermediateOptionalArgsOf2<T1,T2> applyArgs(Optional<T1> t1, Optional<T2> t2) {
            // Go ask "JLS 15.27.3: Type of a Lambda Expression" designer why parametrized method cannot be treated as functional interface
            @SuppressWarnings({"rawtypes", "unchecked"})
            Internal.IntermediateOptionalArgsOf2<T1,T2> result = 
                (Internal.IntermediateOptionalArgsOf2) f -> t1.flatMap(v1 -> t2.map(v2 -> f.apply(v1, v2)));
            return result;
        }

        /**
         * My first design of {@code applyArgs(T1, T2).toFunction(Function2<?,?>)}.
         * IMO {@code lift(Function2<?,?>).args(T1, T2)} feels bit weird; but unlike the former, 
         * this approach is loved by type inference & JLS rules doesn't blame that my code sucks
         */
        public static <T1,T2,Result> FunctionalInterface.FunctionOf2<Optional<T1>,Optional<T2>,Optional<Result>> lift(FunctionalInterface.FunctionOf2<T1,T2,Result> f) {
            return (t1, t2) -> t1.flatMap(v1 -> t2.map(v2 -> f.apply(v1, v2)));
        }

        // Private super namespace, but public namespace -> Let API user uses the available methods but prohibit external import (or FQN) & instantiation
        private enum Internal { ; 
            public interface IntermediateOptionalArgsOf2<T1,T2> { // Functional interface + some convenient mixins
                <Result> Optional<Result> toFunction(FunctionalInterface.FunctionOf2<T1,T2,Result> f);
                /** Unfortunately API "bleed" via private-public can't recognize this was being exposed publicly due to {@link Optionals#applyArgs(Optional, Optional)} */
                @SuppressWarnings("unused")
                default <Result> void toConsumer(FunctionalInterface.ConsumerOf2<T1,T2> f) {
                    this.toFunction((t1, t2) -> { f.accept(t1, t2); return null; });
                }
            }
        }
    }

    // Collector utils
    public enum Collect { ;
        public enum ToList { ;
            // Short-hand & fused version of Collectors.mapping(Function<?,?>, Collectors.toList())
            // collect(Functionals.Collect.ToList.mapFirst(Function<?,?>))
            public static <T,Result> Collector<T,?,List<Result>> mapFirst(Function<? super T,Result> transformer) {
                return ToList.arrayListCollectorNoFinisher((list, element) -> { list.add(transformer.apply(element)); });
            }

            public static <T> Collector<T,?,List<T>> filterNull() {
                return ToList.arrayListCollectorNoFinisher((list, element) -> { if (element != null) list.add(element); });
            }

            public static <T> Collector<Optional<T>,?,List<T>> filterEmptyOptional() {
                return ToList.arrayListCollectorNoFinisher((list, element) -> element.ifPresent(list::add));
            }

            /**
             * Fused distinct List<?> fold collector with customizable key function.
             * Replacing: {@code Stream.filter(StatefulDistinctFilter::filter).toList()}<p/>
             * {@code Stream.collect(Functionals.Collect.ToList.distinctBy(Model::region))}
             */
            public static <T,Key> Collector<T,?,List<T>> distinctBy(Function<T,Key> keyFunction) {
                return Collector.of(
                    () -> new Data.Tuple2<>(new ArrayList<T>(), new ConcurrentHashMap<Key,Object>()),
                    (pair, element) -> {
                        final Key key = keyFunction.apply(element);
                        if (!pair.v2.contains(key)) {
                            pair.v2.put(key, BOGUS_OBJECT);
                            pair.v1.add(element);
                        }
                    },
                    (left, right) -> {
                        left.v1.addAll(right.v1);
                        left.v2.putAll(right.v2);
                        return left;
                    },
                    Data.Tuple2::v1
                );
            }

            private static <T,Result> Collector<T,?,List<Result>> arrayListCollectorNoFinisher(BiConsumer<List<Result>,T> consumer) {
                return Collector.of(
                    ArrayList::new,
                    consumer,
                    (left, right) -> { left.addAll(right); return left; },
                    Collector.Characteristics.IDENTITY_FINISH
                );
            }
        }
    }

    /** Do not mutate this object. */
    private static final Object BOGUS_OBJECT = new Object();
}



/**Experimental design: Currently on work
public final class Try<T> {
    public T consume(Consumer<? super Exception>); // Only way to consume Try<> & short-circuit

    public static <T,E extends Exception> Try<T> fromContext(T fallback, FailableFunction<Try.Catch,? extends T> context);
    public static <T,E extends Exception> Try<T> fromContext(T fallback, FailableSupplier<? extends T> context);
    public static <T,E extends Exception> Try<Optional<T>> fromContext(FailableFunction<Try.Catch,? extends T> context);
    public static <T,E extends Exception> Try<Optional<T>> fromContext(FailableSupplier<? extends T> context);

    public final class Catch {
        public boolean assertNotNull(Object, Supplier<Exception>);
        private <T> Try<T> buildTry(T);
    }

    public enum Throw { ;
        public static Supplier<?> uncheck(FailableSupplier<?>) throws RuntimeException;
        public static Function<?,?> uncheck(FailableFunction<?,?>) throws RuntimeException;
        public static <E extends Exception> void rethrow(E wrapper, Exception e) throws E;
        public static <E extends Exception> void rethrow(E wrapper, FailableSupplier<?> e) throws E;
    }
}

public Try<Optional<Dto>> toDto() {
    return Try.fromContext(catcher -> {
        catcher.assertNotNull(null, InconsistentException::new);
        Try.Throw.rethrow(Exception::new, failableSupplier);
        return null;
    });
}
 */