package com.walmartlabs.concord.bootstrap;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.eclipse.sisu.space.BeanScanning;
import org.eclipse.sisu.space.ClassSpace;
import org.eclipse.sisu.space.SpaceModule;
import org.eclipse.sisu.space.URLClassSpace;
import org.eclipse.sisu.wire.WireModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public final class Bootstrap {

    public static Injector createInjector(ClassLoader cl, Module... modules) {
        Collection<Module> ms = new ArrayList<>();
        if (modules != null) {
            Collections.addAll(ms, modules);
        }

        ClassSpace cs = new URLClassSpace(cl);
        ms.add(new WireModule(new SpaceModule(cs, BeanScanning.CACHE)));

        return Guice.createInjector(ms);
    }

    private Bootstrap() {
    }
}
