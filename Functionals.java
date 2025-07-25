import java.util.ArrayList;
import java.util.Iterator;
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

    public static <T1,T2,R> Function<? super T2,? extends R> argsAt1(FunctionalInterfaces.FunctionOf2<? super T1,? super T2,? extends R> f, T1 arg) {
        return f.argsAt1(arg);
    }

    // This has special interaction: 15.13.1. Compile-Time Declaration of a Method Reference
    // Two different arities, n and n-1, are considered, to account for the possibility that this form refers to either a static method or an instance method.
    public static <T1,T2,R> Function<? super T1,? extends R> argsAt2(FunctionalInterfaces.FunctionOf2<? super T1,? super T2,? extends R> f, T2 arg) {
        return f.argsAt2(arg);
    }

    public static <T1,T2,T3,R> FunctionalInterfaces.FunctionOf2<? super T2,? super T3,? extends R> argsAt1(FunctionalInterfaces.FunctionOf3<? super T1,? super T2,? super T3,? extends R> f, T1 arg) {
        return f.argsAt1(arg);
    }

    public static <T1,T2,T3,R> FunctionalInterfaces.FunctionOf2<? super T1,? super T3,? extends R> argsAt2(FunctionalInterfaces.FunctionOf3<? super T1,? super T2,? super T3,? extends R> f, T2 arg) {
        return f.argsAt2(arg);
    }

    public static <T1,T2,T3,R> FunctionalInterfaces.FunctionOf2<? super T1,? super T2,? extends R> argsAt3(FunctionalInterfaces.FunctionOf3<? super T1,? super T2,? super T3,? extends R> f, T3 arg) {
        return f.argsAt3(arg);
    }

    public static void main(String[] args) {
        Optional.of(Optional.of(10))
            .map(Functionals.argsAt2(Optional::orElse, 3));
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
  * Future edit: Well, I guess Java type system are too weak to utilize this properly.<p/>
  *
  * Plumbing mini-game: Java edition.<p/>
  * Redesigned {@link Functionals#pipe(Function, Function)}, a forward composition builder.<p/>
  * Example:<ul>
  *     <li>{@code Pipe.inlet(Model::dataList).join(Service::transform).outlet(Finisher::fold)}
  *         <ul><li>Produces {@code Function<Model,Folded>} (standard forward composition)</li></ul>
  *     </li>
  *     <li>{@code Pipe.inlet(_ -> 42).outlet(Something::new)}
  *         <ul><li>Produces {@code Function<Object,Something>} (constant function)</li></ul>
  *     </li>
  *     <li>{@code Pipe.inlet(Model::getAttrKey).tee(Objects::isNull)}
  *         <ul><li>Produces {@code Predicate<Model>} (key prober for {@code filter()})</li></ul>
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
    private final Function<? super T,? extends Result> section;
    private Pipe(Function<? super T,? extends Result> f) {
        this.section = f;
    }

    /** Intermediate operation producing new pipeline extended with provided function. */
    public <NewResult> Pipe<T,NewResult> join(Function<? super Result,? extends NewResult> segment) {
        return new Pipe<>(this.section.andThen(segment));
    }

    /** Terminal operation finalize pipeline into fully built {@code Function<T,TerminalResult>}. */
    public <TerminalResult> Function<T,TerminalResult> outlet(Function<? super Result,? extends TerminalResult> end) {
        return (Function<T,TerminalResult>) this.section.andThen(end);
    }

    /**
      * Terminal operation turning pipeline to partitioning function {@code Predicate<T>}. 
      * Especially handy for {@link Stream#filter(Predicate)}---or just {@code outlet(Predicate)::apply} to coerce
      */
    public Predicate<T> tee(Predicate<? super Result> predicate) {
        return x -> predicate.test(this.section.apply(x));
    }

    /** Terminal operation turning pipeline into a sink (implies side-effect). */
    public Consumer<T> sink(Consumer<? super Result> sink) {
        return x -> sink.accept(this.section.apply(x));
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
    public interface FunctionOf2<T1,T2,Result> {
        Result apply(T1 t1, T2 t2);
        default Function<T2,Result> argsAt1(T1 arg) { return t2 -> this.apply(arg, t2); }
        default Function<T1,Result> argsAt2(T2 arg) { return t1 -> this.apply(t1, arg); }
    }
    public interface FunctionOf3<T1,T2,T3,Result> {
        Result apply(T1 t1, T2 t2, T3 t3);
        default FunctionOf2<T2,T3,Result> argsAt1(T1 arg) { return (t2, t3) -> this.apply(arg, t2, t3); }
        default FunctionOf2<T1,T3,Result> argsAt2(T2 arg) { return (t1, t3) -> this.apply(t1, arg, t3); }
        default FunctionOf2<T1,T2,Result> argsAt3(T3 arg) { return (t1, t2) -> this.apply(t1, t2, arg); }
    }

    /** N-ary generalization of {@link java.util.function.Consumer} */
    public interface ConsumerOf2<T1,T2> { void accept(T1 t1, T2 t2); }
}





enum Data { ;
    public static class PanicError extends Error { @Override public String getMessage() { return "Something unthinkable happen"; } }

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

    // Not exactly complete Monad<T>, but good enough in this barren type system land
    public interface ContainerMonad<T> extends Iterable<T> {
        public ToNativeType<T> to();

        // Experimental hierarchical API
        @FunctionalInterface
        public interface ToNativeType<T> {
            public Optional<T> optional();
            public default Stream<T> stream() {
                return this.optional().stream();
            }
        }
    }

    // public interface ParameterizedMonad<T> {}
    // public interface Monad<T> {
    //     public <R,Injected extends Monad<? extends R>> Injected flatMap(Function<? super T,? extends Injected> computation);
    // }

    /** // Terrible ideas: `do` notation
     * public static final class MonadicContext {
     *    T do();
     *    Im1 bind();
     *    Im2 compute();
     * ugh, i had to enumerate the state spaces
     * }
     */
    // refer to object?
    // @OptionalRequestParam(value=RequestParam.value, defaultValue=Nullable.empty()) Nullable
    // @ModelAttribute Nullable<T>
    // @RequestParam Optional<Nullable<T>>
    // after all, all JavaType are nullable
    // it is a noise, but go ask Java why it does that

    // Outflow<T>
    // FlatMapping<?>
    // bind

    // public static ApplicativeFunction<T1,T2,T3,...>

    // Unlike my original test run design, intentionally unbounded type constraint for E
    public static sealed interface Faulty<T,E> extends ContainerMonad<T> {
        public record Error<T,E>(E e) implements Faulty<T,E> {};
        public record Ok<T,E>(T v) implements Faulty<T,E> {};

        @Override public default ToNativeType<T> to() {
            return () -> switch (this) {
                case Error(E _) -> Optional.empty();
                case Ok(T t) -> Optional.of(t);
            };
        }

        @Override public default Iterator<T> iterator() {
            return switch (this) {
                case Error(E _) -> List.<T>of().iterator();
                case Ok(T t) -> List.of(t).iterator();
            };
        }

        // Opted to double monad: Monad<T> + Monad<E>
        public default <T2> Faulty<T2,E> ifOk(Function<? super T,? extends T2> mapper) {
            return switch (this) {
                case Error<T,E> e -> (Error<T2,E>) e; // T in Error type parameter are just a phantom type, everything is equivalent to Error<?,E>
                case Ok(T t) -> new Ok<>(mapper.apply(t));
            };
        }

        public default <T2> Faulty<T2,E> ifOkFlatten(Function<? super T,? extends Faulty<? extends T2,E>> mapper) {
            return switch (this) {
                case Error<T,E> e -> (Error<T2,E>) e;
                case Ok(T t) -> (Faulty<T2,E>) mapper.apply(t); // Faulty<T2,E> :> (? extends Faulty<? extends T2,E>) 
            };
        }

        public default Faulty<T,E> ifOkPeek(Consumer<? super T> inspector) {
            if (this instanceof Ok(T t))
                inspector.accept(t);
            return this;
        }

        public default <E2> Faulty<T,E2> ifError(Function<? super E,? extends E2> mapper) {
            return switch (this) {
                case Error(E e) -> new Error<>(mapper.apply(e));
                case Ok<T,E> o -> (Faulty<T,E2>) o; // Phantom type again
            };
        }

        public default <E2> Faulty<T,E2> ifErrorFlatten(Function<? super E,? extends Faulty<T,? extends E2>> mapper) {
            return switch (this) {
                case Error(E e) -> (Faulty<T,E2>) mapper.apply(e); // Faulty<T,E2> :> (? extends Faulty<T,? extends E2>) 
                case Ok<T,E> o -> (Faulty<T,E2>) o;
            };
        }

        public default Faulty<T,E> ifErrorPeek(Consumer<? super E> inspector) {
            if (this instanceof Error(E e))
                inspector.accept(e);
            return this;
        }
    }

    /**
      * Sum-type value constructors: {@code data Nullable<T> = Has(T) | Empty}.<p/>
      * Provides monad operators for "this value exist" ({@code ifPresent}) & it's natural dual "this value doesn't exist" ({@code ifAbsent}).
      */
    public static sealed interface Nullable<T> extends ContainerMonad<T> {
        public record Has<T>(T value) implements Nullable<T> { @Deprecated public Has { if (value == null) throw new PanicError(); } };

        @SuppressWarnings("rawtypes") // Trust me bro contract: Empty are assignable to any T in Nullable<T>
        public final class Empty implements Nullable {
            private static final Empty EMPTY = new Empty();
            private Empty() {}
            @Override public String toString() { return "Empty"; }
            @Override public int hashCode() { return 0; }
            @Override public boolean equals(Object __) { return false; }
        };

        // ----- Public API -----
        @Override public default ToNativeType<T> to() {
            return () -> switch (this) {
                case Has(T e) -> Optional.of(e);
                case Empty _ -> Optional.empty();
            };
        }

        // My proposal: We slap descriptive name instead of generalized form for monadic context
        // It does cost you some more lengthy name, but whatever, already Stockholm syndrome'd with Java verbosity

        // Mirroring Promise.then(), Nullable.ifPresent() seems way more inline with Nullable's associated monad context
        // Promise.then(): "This computation might already happen or happen in the (unbounded) future, in case it's done, continue do this"
        // Nullable.ifPresent(): "This value might or might not exist, if exist go ahead compute this"
        public default <R> Nullable<R> ifHasValueFlatten(Function<? super T,? extends Nullable<? extends R>> mapper) {
            return switch (this) {
                case Has(T e) -> (Nullable<R>) mapper.apply(e);
                case Empty _ -> Nullable.empty();
            };
        }

        // This is just functor variant of ifPresentFlatten()
        public default <R> Nullable<R> ifHasValue(Function<? super T,? extends R> mapper) {
            return this.ifHasValueFlatten(mapper.andThen(Nullable::of));
        }

        // Now, to replace Optional#ifPresent(), I propose more natural "query" Nullable.peek() for side-effect & still allow composition
        // If only Java type system allow precise ThisType parametrized type constraint, actually it's kind of make sense for ContainerMonad<T> implementation to provide peek()
        // public peek(Consumer<? super T>) -> ThisMonad<T>
        // Even Stream has this method
        public default Nullable<T> ifHasValuePeek(Consumer<? super T> inspector) {
            if (this instanceof Has(T value))
                inspector.accept(value);
            return this;
        }

        @Override public default Iterator<T> iterator() {
            return switch (this) {
                case Has(T e) -> List.of(e).iterator();
                case Empty _ -> List.<T>of().iterator();
            };
        }

        // Hmm, actually I didn't know that record's canonical constructor has a restriction on visibility modifier
        // But in this case, this static factory constructor is still useful
        public static <T> Nullable<T> of(T value) {
            return value == null ? Nullable.empty() : new Has<>(value);
        }

        public static <T> Nullable<T> empty() {
            return (Nullable<T>) Empty.EMPTY;
        }
    }
}





// Optional utilities
enum Optionals { ;
    // Meh, go with sealed Nullable

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