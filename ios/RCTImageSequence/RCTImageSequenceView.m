//
// Created by Mads Lee Jensen on 07/07/16.
// Copyright (c) 2016 Facebook. All rights reserved.
//

#import "RCTImageSequenceView.h"

@implementation RCTImageSequenceView {
    NSUInteger _framesPerSecond;
    NSMutableDictionary *_activeTasks;
    NSMutableDictionary *_imagesLoaded;
    BOOL _loop;
}

- (void)setFramesPerSecond:(NSUInteger)framesPerSecond {
    _framesPerSecond = framesPerSecond;

    if (self.animationImages.count > 0) {
        self.animationDuration = self.animationImages.count * (1.0f / _framesPerSecond);
    }
}

- (void)setLoop:(NSUInteger)loop {
    _loop = loop;

    self.animationRepeatCount = _loop ? 0 : 1;
}

- (void)setImages:(NSArray *)images {
    __weak RCTImageSequenceView *weakSelf = self;

    self.animationImages = nil;

    _activeTasks = [NSMutableDictionary new];
    _imagesLoaded = [NSMutableDictionary new];

    for (NSUInteger index = 0; index < images.count; index++) {
        NSDictionary *item = images[index];

        #ifdef DEBUG
        NSString *url = item[@"uri"];
        #else
        NSString *url = [NSString stringWithFormat:@"file://%@", item[@"uri"]]; // when not in debug, the paths are "local paths" (because resources are bundled in app)
        #endif

        dispatch_async(dispatch_queue_create("image_sequence.downloader", NULL), ^{
            UIImage *image = [UIImage imageWithData:[NSData dataWithContentsOfURL:[NSURL URLWithString:url]]];

            dispatch_async(dispatch_get_main_queue(), ^{
                [weakSelf onImageLoadTaskAtIndex:index image:image];
            });
        });

        _activeTasks[@(index)] = url;
    }
}

- (void)onImageLoadTaskAtIndex:(NSUInteger)index image:(UIImage *)image {
    [_activeTasks removeObjectForKey:@(index)];

    _imagesLoaded[@(index)] = image;

    if (_activeTasks.allValues.count == 0) {
        [self onImagesLoaded];
    }
}

- (void)onImagesLoaded {
    NSMutableArray *images = [NSMutableArray new];
    for (NSUInteger index = 0; index < _imagesLoaded.allValues.count; index++) {
        UIImage *image = _imagesLoaded[@(index)];
        [images addObject:image];
    }

    [_imagesLoaded removeAllObjects];

    self.animationDuration = images.count * (1.0f / _framesPerSecond);
    self.animationImages = images;
    self.animationRepeatCount = _loop ? 0 : 1;
    [self startAnimating];

    if (_onLoadImageCompleted != nil) {
        _onLoadImageCompleted(@{@"loadCompleted": @TRUE});
    }

    [self setupEndAnimationEvent];
}

- (void)setupEndAnimationEvent {
    dispatch_time_t popTime = dispatch_time(DISPATCH_TIME_NOW, self.animationDuration * NSEC_PER_SEC);
    dispatch_after(popTime, dispatch_get_main_queue(), ^(void){
        if (self.onAnimationFinished != nil) {
            self.onAnimationFinished(@{@"animationFinished": @TRUE});
        }
        [self stopAnimating];
    });
}

@end
