import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.IntStream;
import java.util.stream.Stream;



/**
 * Java functional programming utilities.
 * Designed to be zero external dependency.
 */
public enum Functionals { ; // Namespace language construct via empty-enum
    /**
     * Fluent functional cast operator.<p/>
     * Ex: {@code Functionals.cast(obj).to(Target.class).ifPresent(System.out::println);}
     */
    @SuppressWarnings({"rawtypes"})
    public static Internal.Castable<?> cast(Object obj) {
        return (Internal.Castable) clazz -> clazz.isInstance(obj) ? Optional.of(clazz.cast(obj)) : Optional.empty();
    }

    private enum Internal { ;
        /** 
          * Turns out my {@link Optionals.Internal.IntermediateOptionalArgsOf2} abuses raw-types to work.
          * I had to introduce phantom type for this to work with lambdas
          */
        public interface Castable<Phantom> { <TargetType> Optional<TargetType> to(Class<TargetType> clazz); }
    }
}





/**
  * New variant of {@link Functionals#pipe(Function, Function)}, forward composition builder.<p/>
  * Plumbing mini-game.<p/>
  * Example:<ul>
  *     <li>{@code Pipe.inlet(Model::dataList).join(Service::filter).outlet(Finisher::fold)}
  *         <ul><li>Produces {@code Function<Model,Folded>} (standard forward composition)</li></ul>
  *     </li>
  *     <li>{@code Pipe.inlet(_ -> 42).outlet(Something::new)}
  *         <ul><li>Produces {@code Function<Object,Something>} (constant function composition)</li></ul>
  *     </li>
  *     <li>{@code Pipe.inlet(Model::getAttrKey).tee(Objects::isNull)}
  *         <ul><li>Produces {@code Predicate<Model>} (key prober pipeline for {@code filter()})</li></ul>
  *     </li>
  *     <li><code>Pipe.inlet(Model::getAttr).join(a -> { a.mutate(); return a; }).sink(Model::setAttr)</code>
  *         <ul><li>Produces {@code Consumer<Model>} (pipeline that ends with side-effect)</li></ul>
  *     </li>
  *     <li>{@code Pipe.source(Config::getX).join(Transform::process).sink(ServiceConfigurator::setConfig)}
  *         <ul><li>Produces {@code Consumer<Void>} (fully side-effect "procedure")</li></ul>
  *     </li>
  * </ul>
  */
final class Pipe<T,Result> {
    private final Function<? super T,? extends Result> source;
    private Pipe(Function<? super T,? extends Result> f) {
        this.source = f;
    }

    /** Intermediate operation producing new pipeline extended with provided function. */
    public <NewResult> Pipe<T,NewResult> join(Function<? super Result,? extends NewResult> segment) {
        return new Pipe<>(this.source.andThen(segment));
    }

    /** Terminal operation finalize pipeline into fully built {@code Function<T,TerminalResult>}. */
    public <TerminalResult> Function<T,TerminalResult> outlet(Function<? super Result,? extends TerminalResult> end) {
        return (Function<T,TerminalResult>) this.source.andThen(end);
    }

    /**
      * Terminal operation turning pipeline to partitioning function {@code Predicate<T>}. 
      * Especially handy for {@link Stream#filter(Predicate)}---or just {@code outlet(Predicate)::apply} to coerce
      */
    public Predicate<T> tee(Predicate<? super Result> predicate) {
        return x -> predicate.test(source.apply(x));
    }

    /** Terminal operation turning pipeline into a sink (implies side-effect). */
    public Consumer<T> sink(Consumer<? super Result> sink) {
        return x -> sink.accept(this.source.apply(x));
    }

    /**
      * Create pipeline with provided function as initial pipe inlet.<p/>
      * As Java type system inferrence is pretty weak, explicit type annotation might be required.
      */
    public static <T,Result> Pipe<T,Result> inlet(Function<? super T,? extends Result> base) {
        return new Pipe<>(base);
    }

    /**
      * {@link Supplier} factory variant of {@link #inlet(Function)} (side-effect data source or constant supplier).<p/>
      * Treat {@code Void} as unit type and use {@code null} for args.
      */
    public static <Result> Pipe<Void,Result> source(Supplier<? extends Result> source) {
        return new Pipe<>(_ -> source.get());
    }
}





// Functional interfaces
enum FunctionalInterfaces { ;
    /** N-ary generalization of {@link java.util.function.Function} */
    public interface FunctionOf2<T1,T2,Result> { Result apply(T1 t1, T2 t2); }
    public interface FunctionOf3<T1,T2,T3,Result> { Result apply(T1 t1, T2 t2, T3 t3); }

    /** N-ary generalization of {@link java.util.function.Consumer} */
    public interface ConsumerOf2<T1,T2> { void accept(T1 t1, T2 t2); }
}





enum Data { ;
    public static record Tuple2<T1,T2>(T1 v1, T2 v2) {}
    // Budget pre-record JDK 17 edition
    public static final class TupleOf2<T1,T2> {
        public final T1 v1;
        public final T2 v2;

        public TupleOf2(T1 v1, T2 v2) {
            this.v1 = v1;
            this.v2 = v2;
        }
    }
}





// Optional utilities
enum Optionals { ;
    // Functionals.applyArgs(Optional.of(10), Optional.of("hello")).toFunction((number, str) -> str + Integer.toString(number))
    public static <T1,T2> Internal.IntermediateOptionalArgsOf2<T1,T2> applyArgs(Optional<T1> t1, Optional<T2> t2) {
        // Go ask "JLS 15.27.3: Type of a Lambda Expression" designer why parametrized method cannot be treated as functional interface
        @SuppressWarnings({"rawtypes"})
        Internal.IntermediateOptionalArgsOf2<T1,T2> result = 
            (Internal.IntermediateOptionalArgsOf2) f -> t1.flatMap(v1 -> t2.map(v2 -> f.apply(v1, v2)));
        return result;
    }

    /**
     * My first design of {@code applyArgs(T1, T2).toFunction(Function2<?,?>)}.
     * IMO {@code lift(Function2<?,?>).args(T1, T2)} feels bit weird; but unlike the former, 
     * this approach is loved by type inference & JLS rules doesn't blame that my code sucks
     */
    public static <T1,T2,Result> FunctionalInterfaces.FunctionOf2<Optional<T1>,Optional<T2>,Optional<Result>> lift(FunctionalInterfaces.FunctionOf2<T1,T2,Result> f) {
        return (t1, t2) -> t1.flatMap(v1 -> t2.map(v2 -> f.apply(v1, v2)));
    }

    // Private super namespace, but public namespace -> Let API user uses the available methods but prohibit external import (or FQN) & instantiation
    private enum Internal { ; 
        public interface IntermediateOptionalArgsOf2<T1,T2> { // Functional interface + some convenient mixins
            <Result> Optional<Result> toFunction(FunctionalInterfaces.FunctionOf2<T1,T2,Result> f);
            /** Unfortunately API "bleed" via private-public can't recognize this was being exposed publicly due to {@link Optionals#applyArgs(Optional, Optional)} */
            @SuppressWarnings("unused")
            default <Result> void toConsumer(FunctionalInterfaces.ConsumerOf2<T1,T2> f) {
                this.toFunction((t1, t2) -> { f.accept(t1, t2); return null; });
            }
        }
    }
}





enum Functors { ;
    // Predicate utilities primarily designed for filter()
    public enum Filter { ;
        public static <T,Key> Predicate<T> keyEquals(Function<? super T,? extends Key> keyFunction, Key value) {
            return v -> Objects.equals(value, keyFunction.apply(v));
        }
    }

    public enum Map { ;
    }
}





enum Streams { ;
    /** Lazy stream factory method for supplier. Finite stream & 1x invocation, unlike {@link Stream#generate(Supplier) */
    public static <T> Stream<T> of(Supplier<? extends T> supplier) {
        return IntStream.of(0).mapToObj(_ -> supplier.get());
    }

    public enum Collect { ;
        // General fold / reduce collector
        public enum Fold { ;
            /**
              * Direct all stream element into {@link Consumer} sink without materializing any container.<p/>
              * By default support all {@link Collector.Characteristics}, but depend on provided {@link Consumer}.<p/>
              */
            public static <T> Collector<T,?,Void> sink(Consumer<? super T> consumer) {
                return Collector.of(
                    () -> null,
                    (_, element) -> consumer.accept(element),
                    (_, _) -> null,
                    Collector.Characteristics.CONCURRENT,
                    Collector.Characteristics.IDENTITY_FINISH,
                    Collector.Characteristics.UNORDERED
                );
            }
        }

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
                        if (!pair.v2().contains(key)) {
                            pair.v2().put(key, BOGUS_OBJECT);
                            pair.v1().add(element);
                        }
                    },
                    (left, right) -> {
                        left.v1().addAll(right.v1());
                        left.v2().putAll(right.v2());
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

            /** Do not mutate this object. */
            private static final Object BOGUS_OBJECT = new Object();
        }
    }
}





/**Experimental design: Still WIP, some further testing needed
final class Try<T> {
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