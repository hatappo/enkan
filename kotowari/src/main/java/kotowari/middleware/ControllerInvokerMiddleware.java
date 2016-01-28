package kotowari.middleware;

import enkan.Middleware;
import enkan.MiddlewareChain;
import enkan.component.SystemComponent;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.data.Traceable;
import enkan.exception.MisconfigurationException;
import enkan.exception.UnreachableException;
import enkan.exception.UnrecoverableException;
import enkan.system.inject.ComponentInjector;
import kotowari.data.FormAvailable;
import kotowari.data.Routable;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static enkan.util.ReflectionUtils.tryReflection;

/**
 * Kotowari endpoint.
 *
 * This middleware inject arguments to controller method.
 *
 * <ul>
 *     <li>HttpRequest - Inject a request object.</li>
 *     <li>Map - Inject parsed request parameters.</li>
 *     <li>JavaBean - Inject form.</li>
 * </ul>
 *
 * @author kawasima
 */
@enkan.annotation.Middleware(name = "controllerInvoker", dependencies = "params")
public class ControllerInvokerMiddleware<RES> implements Middleware<HttpRequest, RES> {
    Map<Class<?>, Object> controllerCache = new ConcurrentHashMap<>();
    ComponentInjector injector;

    public ControllerInvokerMiddleware(ComponentInjector injector) {
        this.injector = injector;
    }

    protected Object[] createArguments(HttpRequest request) {
        Method method = ((Routable) request).getControllerMethod();
        Serializable form = FormAvailable.class.cast(request).getForm();
        Object[] arguments = new Object[method.getParameterCount()];

        int parameterIndex = 0;
        for (Parameter parameter : method.getParameters()) {
            Class<?> type = parameter.getType();
            if (HttpRequest.class.isAssignableFrom(type)) {
                arguments[parameterIndex] = request;
            } else if (Map.class.isAssignableFrom(type)) {
                arguments[parameterIndex] = request.getParams().toMap();
            } else if (form != null && form.getClass().equals(type)) {
                arguments[parameterIndex] = form;
            }
            parameterIndex++;
        }
        return arguments;
    }

    private Object inject(Object controller) {
        if (injector != null) {
            injector.inject(controller);
        }
        return controller;
    }

    @Override
    public RES handle(HttpRequest request, MiddlewareChain next) {
        if (request instanceof Routable) {
            Method controllerMethod = ((Routable) request).getControllerMethod();
            Class<?> controllerClass = controllerMethod.getDeclaringClass();

            Object controller = controllerCache.computeIfAbsent(controllerClass, c ->
                    tryReflection(() -> inject(c.newInstance())));

            return tryReflection(() -> {
                Object[] arguments = createArguments(request);
                return (RES) controllerMethod.invoke(controller, arguments);
            });
        } else {
            MisconfigurationException.raise("DONT_IMPLEMENT", Routable.class);
            throw UnreachableException.create();
        }
    }
}
