import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collector;



/**
 * Java functional programming utilities.
 * Designed to be zero external dependency.
 */
public enum Functionals { ; // Namespace language construct via empty-enum
    // Functionals.applyArgs(Optional.of(10), Optional.of("hello")).toFunction((number, str) -> str + Integer.toString(number))
    public static <T1,T2> OptionalFunction.IntermediateOptionalArgsOf2<T1,T2> applyArgs(Optional<T1> t1, Optional<T2> t2) {
        @SuppressWarnings({"rawtypes", "unchecked"}) // Go ask JLS why parametrized method cannot be treated as functional interface
        OptionalFunction.IntermediateOptionalArgsOf2<T1,T2> result = (OptionalFunction.IntermediateOptionalArgsOf2) f -> t1.flatMap(v1 -> t2.map(v2 -> f.apply(v1, v2)));
        return result;
    }

    /**
     * My first design of {@code applyArgs(T1, T2).toFunction(Function2<?,?>)}.
     * IMO {@code lift(Function2<?,?>).args(T1, T2)} feels bit weird; but unlike the former, 
     * this approach is loved by type inference & JLS rules doesn't blame that my code sucks
     */
    public static <T1,T2,R> FunctionNAry.Function2Ary<Optional<T1>,Optional<T2>,Optional<R>> lift(FunctionNAry.Function2Ary<T1,T2,R> f) {
        return (t1, t2) -> t1.flatMap(v1 -> t2.map(v2 -> f.apply(v1, v2)));
    }

    // Very pointless and method-reference oriented. Functionals.pipe(Map.Entry::getKey, Model::data1);
    public static <T1,T2,Result> Function<T1,Result> pipe(Function<T1,? extends T2> f1, Function<? super T2,Result> f2) {
        return f1.andThen(f2);
    }

    /** See {@link #pipe(Function,Function)}. {@code Functionals.pipe(Model::dataList, Service::filter, Finisher::fold)} */
    public static <T1,T2,T3,Result> Function<T1,Result> pipe(
        Function<T1,? extends T2> f1, Function<? super T2,? extends T3> f2, Function<? super T3,Result> f3
    ) {
        return f1.andThen(f2).andThen(f3);
    }

    // N-ary generalization of Function<?,?>
    public enum FunctionNAry { ;
        public interface Function2Ary<T1,T2,R> { R apply(T1 t1, T2 t2); }
        public interface Function3Ary<T1,T2,T3,R> { R apply(T1 t1, T2 t2, T3 t3); }
    }

    // Collector utils
    public enum Collect { ;
        public enum ToList { ;
            // Short-hand & fused version of Collectors.mapping(Function<?,?>, Collectors.toList())
            // collect(Functionals.Collect.ToList.mapFirst(Function<?,?>))
            public static <T,R> Collector<T,?,List<R>> mapFirst(Function<? super T,R> transformer) {
                return ToList.arrayListCollectorNoFinisher((list, element) -> { list.add(transformer.apply(element)); });
            }

            public static <T> Collector<T,?,List<T>> filterNull() {
                return ToList.arrayListCollectorNoFinisher((list, element) -> { if (element != null) list.add(element); });
            }

            public static <T> Collector<Optional<T>,?,List<T>> filterEmptyOptional() {
                return ToList.arrayListCollectorNoFinisher((list, element) -> element.ifPresent(list::add));
            }

            private static final Object BOGUS_OBJECT = new Object();
            /**
             * Fused distinct List<?> fold collector with customizable key function.
             * Replacing: {@code Stream.filter(StatefulDistinctFilter::filter).toList()}<p/>
             * {@code Stream.collect(Functionals.Collect.ToList.distinctBy(Model::region))}
             */
            public static <T,Key> Collector<T,?,List<T>> distinctBy(Function<T,Key> keyFunction) {
                return Collector.of(
                    () -> new Tuple.TupleOf2<>(new ArrayList<T>(), new ConcurrentHashMap<Key,Object>()),
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
                    Tuple.TupleOf2::v1
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

    public enum Tuple { ;
        public record TupleOf2<T1,T2>(T1 v1, T2 v2) {}
    }



    /** ----- Internal ----- */
    // Private super namespace, but public namespace -> Let API user uses the available methods but prohibit external import (or FQN) & instantiation
    private enum OptionalFunction { ; 
        public interface IntermediateOptionalArgsOf2<T1,T2> { <R> Optional<R> toFunction(FunctionNAry.Function2Ary<T1,T2,R> f); }
    }
}
