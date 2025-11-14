import type { TurboModule } from 'react-native'
import { TurboModuleRegistry } from 'react-native'
import { Source, PreloadAwaitResult, FastImageQueryCacheResult } from './index'

export interface Spec extends TurboModule {
    preload: (sources: Source[]) => void
    preloadAwait(sources: Source[]): Promise<PreloadAwaitResult>
    queryCache(urls: string[]): Promise<FastImageQueryCacheResult>
    clearMemoryCache: () => Promise<void>
    clearDiskCache: () => Promise<void>
}

export default TurboModuleRegistry.getEnforcing<Spec>('FastImageViewModule')
