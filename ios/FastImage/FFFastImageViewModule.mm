#import "FFFastImageViewModule.h"
#import "FFFastImageSource.h"

#import <SDWebImage/SDWebImage.h>
#import <SDWebImage/SDImageCache.h>
#import <SDWebImage/SDWebImagePrefetcher.h>
#import <SDWebImage/SDWebImageDownloader.h>

@implementation FFFastImageViewModule

RCT_EXPORT_MODULE(FastImageViewModule)

RCT_EXPORT_METHOD(preload:(nonnull NSArray<FFFastImageSource *> *)sources)
{
    NSMutableArray *urls = [NSMutableArray arrayWithCapacity:sources.count];

    [sources enumerateObjectsUsingBlock:^(FFFastImageSource * _Nonnull source, NSUInteger idx, BOOL * _Nonnull stop) {
        [source.headers enumerateKeysAndObjectsUsingBlock:^(NSString *key, NSString* header, BOOL *stop) {
            [[SDWebImageDownloader sharedDownloader] setValue:header forHTTPHeaderField:key];
        }];
        [urls setObject:source.url atIndexedSubscript:idx];
    }];

    [[SDWebImagePrefetcher sharedImagePrefetcher] prefetchURLs:urls];
}

RCT_REMAP_METHOD(preloadAwait,
                 preloadAwait:(nonnull NSArray<FFFastImageSource *> *)sources
                 resolve:(RCTPromiseResolveBlock)resolve
                 reject:(RCTPromiseRejectBlock)reject)
{
    if (sources.count == 0) {
      resolve(@{ @"finished": @0, @"skipped": @0 });
      return;
    }

    NSMutableArray<NSURL *> *urls = [NSMutableArray arrayWithCapacity:sources.count];
    [sources enumerateObjectsUsingBlock:^(FFFastImageSource * _Nonnull source, NSUInteger idx, BOOL * _Nonnull stop) {
        [source.headers enumerateKeysAndObjectsUsingBlock:^(NSString *key, NSString *header, BOOL *stop) {
            [[SDWebImageDownloader sharedDownloader] setValue:header forHTTPHeaderField:key];
        }];
        [urls addObject:source.url];
    }];

    SDWebImagePrefetcher *prefetcher = [SDWebImagePrefetcher sharedImagePrefetcher];
    [prefetcher prefetchURLs:urls
                progress:nil
                completed:^(NSUInteger finishedCount, NSUInteger skippedCount) {
        resolve(@{ @"finished": @(finishedCount), @"skipped": @(skippedCount) });
    }];
}

RCT_REMAP_METHOD(queryCache,
                 queryCache:(nonnull NSArray<NSString *> *)urlStrings
                 resolve:(RCTPromiseResolveBlock)resolve
                 reject:(RCTPromiseRejectBlock)reject)
{
    SDImageCache *cache = [SDImageCache sharedImageCache];
    SDWebImageManager *manager = [SDWebImageManager sharedManager];

    NSMutableDictionary *result = [NSMutableDictionary new];
    __block NSInteger remaining = urlStrings.count;

    if (remaining == 0) {
        resolve(result);
        return;
    }

    for (NSString *urlString in urlStrings) {
        NSURL *url = [NSURL URLWithString:urlString];
        NSString *key = [manager cacheKeyForURL:url];

        [cache containsImageForKey:key
               cacheType:SDImageCacheTypeAll
               completion:^(SDImageCacheType containsCacheType) {
            if (containsCacheType == SDImageCacheTypeMemory) {
                result[urlString] = @"memory";
            } else if (containsCacheType == SDImageCacheTypeDisk) {
                result[urlString] = @"disk";
            }
            if (--remaining == 0) {
                resolve(result);
            }
        }];
    }
}

RCT_EXPORT_METHOD(clearMemoryCache:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
{
    [SDImageCache.sharedImageCache clearMemory];
    resolve(NULL);
}

RCT_EXPORT_METHOD(clearDiskCache:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
{
    [SDImageCache.sharedImageCache clearDiskOnCompletion:^(){
        resolve(NULL);
    }];
}
#ifdef RCT_NEW_ARCH_ENABLED
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeFastImageViewModuleSpecJSI>(params);
}
#endif

@end
