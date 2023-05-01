package com.walmartlabs.concord.sisu;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Qualifier;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateBinder;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.MembersInjectorLookup;
import com.google.inject.spi.PrivateElements;
import com.google.inject.spi.ProviderLookup;
import org.eclipse.sisu.space.*;


public class SpaceModule_ implements Module
{
    // ----------------------------------------------------------------------
    // Constants
    // ----------------------------------------------------------------------

    static final String INDEX_FOLDER = "META-INF/sisu/";
    static final String NAMED = "javax.inject.Named";

    private static final String NAMED_INDEX = /*AbstractSisuIndex.*/INDEX_FOLDER + /*AbstractSisuIndex.*/NAMED;

    public static final ClassFinder LOCAL_INDEX = new IndexedClassFinder( NAMED_INDEX, false );

    public static final ClassFinder GLOBAL_INDEX = new IndexedClassFinder( NAMED_INDEX, true );

    public static final ClassFinder LOCAL_SCAN = SpaceScanner_.DEFAULT_FINDER;

    // ----------------------------------------------------------------------
    // Implementation fields
    // ----------------------------------------------------------------------

    private static final class RecordedElements
    {
        static final ConcurrentMap<String, List<Element>> cache = //
                new ConcurrentHashMap<String, List<Element>>( 16, 0.75f, 1 );
    }

    private final boolean caching;

    private final ClassSpace space;

    private final ClassFinder finder;

    private SpaceModule_.Strategy strategy = SpaceModule_.Strategy.DEFAULT;

    // ----------------------------------------------------------------------
    // Constructors
    // ----------------------------------------------------------------------

    public SpaceModule_( final ClassSpace space )
    {
        this( space, BeanScanning.ON );
    }

    public SpaceModule_( final ClassSpace space, final ClassFinder finder )
    {
        caching = false;

        this.space = space;
        this.finder = finder;
    }

    public SpaceModule_( final ClassSpace space, final BeanScanning scanning )
    {
        caching = BeanScanning.CACHE == scanning;

        this.space = space;
        switch ( scanning )
        {
            case OFF:
                finder = null;
                break;
            case INDEX:
                finder = LOCAL_INDEX;
                break;
            case GLOBAL_INDEX:
                finder = GLOBAL_INDEX;
                break;
            default:
                finder = LOCAL_SCAN;
                break;
        }
    }

    // ----------------------------------------------------------------------
    // Public methods
    // ----------------------------------------------------------------------

    /**
     * Applies a new visitor {@link SpaceModule.Strategy} to the current module.
     *
     * @param _strategy The new strategy
     * @return Updated module
     */
    public Module with( final SpaceModule_.Strategy _strategy )
    {
        strategy = _strategy;
        return this;
    }

    public void configure( final Binder binder )
    {
        binder.bind( ClassSpace.class ).toInstance( space );

        if ( caching )
        {
            recordAndReplayElements( binder );
        }
        else if ( null != finder )
        {
            scanForElements( binder );
        }
    }

    // ----------------------------------------------------------------------
    // Public types
    // ----------------------------------------------------------------------

    /**
     * Visitor strategy.
     */
    public interface Strategy
    {
        /**
         * Selects the {@link SpaceVisitor} to be used for the given {@link Binder}.
         *
         * @param binder The binder
         * @return Selected visitor
         */
        SpaceVisitor visitor( Binder binder );

        /**
         * Default visitor strategy; scan and bind implementations with {@link Qualifier}s.
         */
        SpaceModule_.Strategy DEFAULT = new SpaceModule_.Strategy()
        {
            public SpaceVisitor visitor( final Binder binder )
            {
                return new QualifiedTypeVisitor( new QualifiedTypeBinder( binder ) );
            }
        };
    }

    // ----------------------------------------------------------------------
    // Implementation methods
    // ----------------------------------------------------------------------

    void scanForElements( final Binder binder )
    {
        new SpaceScanner_( space, finder ).accept( strategy.visitor( binder ) );
    }

    private void recordAndReplayElements( final Binder binder )
    {
        final String key = space.toString();
        List<Element> elements = SpaceModule_.RecordedElements.cache.get( key );
        if ( null == elements )
        {
            // record results of scanning plus any custom module bindings
            final List<Element> recording = Elements.getElements( new Module()
            {
                public void configure( final Binder recorder )
                {
                    scanForElements( recorder );
                }
            } );
            elements = SpaceModule_.RecordedElements.cache.putIfAbsent( key, recording );
            if ( null == elements )
            {
                // shortcut, no need to reset state first time round
                Elements.getModule( recording ).configure( binder );
                return;
            }
        }

        replayRecordedElements( binder, elements );
    }

    private static void replayRecordedElements( final Binder binder, final List<Element> elements )
    {
        for ( final Element e : elements )
        {
            // lookups have state so we replace them with duplicates when replaying...
            if ( e instanceof ProviderLookup<?> )
            {
                binder.getProvider( ( (ProviderLookup<?>) e ).getKey() );
            }
            else if ( e instanceof MembersInjectorLookup<?> )
            {
                binder.getMembersInjector( ( (MembersInjectorLookup<?>) e ).getType() );
            }
            else if ( e instanceof PrivateElements )
            {
                // Follows example set by Guice Modules when applying private elements:
                final PrivateElements privateElements = (PrivateElements) e;

                // 1. create new private binder, using the elements source token
                final PrivateBinder privateBinder = binder.withSource( e.getSource() ).newPrivateBinder();

                // 2. for all elements, apply each element to the private binder
                replayRecordedElements( privateBinder, privateElements.getElements() );

                // 3. re-expose any exposed keys using their exposed source token
                for ( final Key<?> k : privateElements.getExposedKeys() )
                {
                    privateBinder.withSource( privateElements.getExposedSource( k ) ).expose( k );
                }
            }
            else
            {
                e.applyTo( binder );
            }
        }
    }
}
