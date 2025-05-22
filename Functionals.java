import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collector;



/**
 * Java functional programming utilities.
 * Designed to be zero external dependency.
 */
public enum Functionals { ; // Namespace language construct via empty-enum
    // Functionals.applyArgs(Optional.of(10), Optional.of("hello")).toFunction((number, str) -> str + Integer.toString(number))
    public static <T1,T2> OptionalFunction.IntermediateOptionalFunctionOf2<T1,T2> applyArgs(Optional<T1> t1, Optional<T2> t2) {
        @SuppressWarnings({"rawtypes", "unchecked"}) // Go ask JLS why parametrized method cannot be treated as functional interface
        OptionalFunction.IntermediateOptionalFunctionOf2<T1,T2> result = (OptionalFunction.IntermediateOptionalFunctionOf2) f -> t1.flatMap(v1 -> t2.map(v2 -> f.apply(v1, v2)));
        return result;
    }

    // Very pointless and method-reference oriented. Functionals.pipe(Map.Entry::getKey, Model::data1);
    public static <T1,T2,R> Function<T1,R> pipe(Function<T1,? extends T2> f1, Function<? super T2,R> f2) {
        return f1.andThen(f2);
    }

    // N-ary generalization of Function<?,?>
    public enum FunctionNAry { ;
        public interface Function2Ary<T1,T2,R> { R apply(T1 t1, T2 t2); }
        public interface Function3Ary<T1,T2,T3,R> { R apply(T1 t1, T2 t2, T3 t3); }
    }

    // Collector utils
    public enum Collect { ;
        public enum ToList { ;
            public <T> Collector<T,?,List<T>> filterNull() {
                return Collector.of(
                    ArrayList::new,
                    (list, element) -> { if (element != null) list.add(element); },
                    (left, right) -> { left.addAll(right); return left; },
                    Collector.Characteristics.IDENTITY_FINISH
                );
            }

            public <T> Collector<Optional<T>,?,List<T>> filterEmptyOptional() {
                return Collector.of(
                    ArrayList::new,
                    (list, element) -> element.ifPresent(list::add),
                    (left, right) -> { left.addAll(right); return left; },
                    Collector.Characteristics.IDENTITY_FINISH
                );
            }
        }
    }



    /** ----- Internal ----- */
    private enum OptionalFunction { ;
        public interface IntermediateOptionalFunctionOf2<T1,T2> { <R> R toFunction(FunctionNAry.Function2Ary<T1,T2,R> f); }
    }
}
