(function() {
    try {
        // Clone document to avoid modifying active web page
        var clone = document.body.cloneNode(true);

        // Remove junk & noise elements
        var junkSelectors = [
            'nav', 'header', 'footer', 'aside', 'script', 'style', 'noscript', 'iframe', 'form',
            '.ad', '.ads', '.advertisement', '.sidebar', '.comments', '.comment-section',
            '.social-share', '.share-buttons', '.cookie-banner', '.newsletter-signup',
            '[role="navigation"]', '[role="banner"]', '[role="complementary"]', '[aria-hidden="true"]'
        ];
        junkSelectors.forEach(function(sel) {
            var junk = clone.querySelectorAll(sel);
            for (var i = 0; i < junk.length; i++) {
                junk[i].parentNode && junk[i].parentNode.removeChild(junk[i]);
            }
        });

        // Find candidate article element
        var candidate = clone.querySelector('article') || clone.querySelector('[role="main"]') || clone.querySelector('main');

        if (!candidate) {
            // Heuristic scoring: find container with maximum paragraph text density
            var containers = clone.querySelectorAll('div, section');
            var bestScore = 0;
            var bestElement = clone;

            for (var c = 0; c < containers.length; c++) {
                var el = containers[c];
                var ps = el.querySelectorAll('p');
                var textLen = el.innerText ? el.innerText.trim().length : 0;
                var score = (ps.length * 30) + (textLen / 20);
                if (score > bestScore) {
                    bestScore = score;
                    bestElement = el;
                }
            }
            candidate = bestElement;
        }

        // Clean element attributes & tags
        var allowedTags = {
            'P': true, 'H1': true, 'H2': true, 'H3': true, 'H4': true, 'H5': true, 'H6': true,
            'BLOCKQUOTE': true, 'PRE': true, 'CODE': true, 'UL': true, 'OL': true, 'LI': true,
            'IMG': true, 'A': true, 'TABLE': true, 'THEAD': true, 'TBODY': true, 'TR': true,
            'TH': true, 'TD': true, 'B': true, 'STRONG': true, 'I': true, 'EM': true, 'HR': true
        };

        // Convert images to absolute URLs
        var images = candidate.querySelectorAll('img');
        for (var imgIdx = 0; imgIdx < images.length; imgIdx++) {
            var img = images[imgIdx];
            if (img.src) {
                img.setAttribute('src', img.src);
            }
            img.removeAttribute('srcset');
            img.removeAttribute('loading');
            img.removeAttribute('style');
            img.removeAttribute('class');
            img.removeAttribute('onclick');
        }

        // Clean links
        var links = candidate.querySelectorAll('a');
        for (var aIdx = 0; aIdx < links.length; aIdx++) {
            var a = links[aIdx];
            if (a.href) {
                a.setAttribute('href', a.href);
            }
            a.removeAttribute('style');
            a.removeAttribute('class');
            a.removeAttribute('onclick');
        }

        // Extract metadata
        var metaTitle = document.querySelector('meta[property="og:title"]') || document.querySelector('meta[name="twitter:title"]');
        var pageTitle = metaTitle ? metaTitle.getAttribute('content') : '';
        if (!pageTitle) {
            var h1 = document.querySelector('h1');
            pageTitle = h1 ? h1.innerText.trim() : document.title;
        }

        var metaAuthor = document.querySelector('meta[name="author"]') || document.querySelector('[rel="author"]') || document.querySelector('.byline');
        var authorName = metaAuthor ? (metaAuthor.getAttribute('content') || metaAuthor.innerText || '') : '';

        var metaSite = document.querySelector('meta[property="og:site_name"]');
        var siteName = metaSite ? metaSite.getAttribute('content') : window.location.hostname;

        var rawText = candidate.innerText || '';
        var words = rawText.trim().split(/\s+/).filter(function(w) { return w.length > 0; });
        var wordCount = words.length;
        var readTimeMinutes = Math.max(1, Math.ceil(wordCount / 200));

        return JSON.stringify({
            title: pageTitle.trim(),
            byline: authorName.trim(),
            siteName: siteName.trim(),
            contentHtml: candidate.innerHTML,
            wordCount: wordCount,
            readTimeMinutes: readTimeMinutes
        });
    } catch (e) {
        return JSON.stringify({
            title: document.title,
            byline: '',
            siteName: window.location.hostname,
            contentHtml: document.body ? document.body.innerHTML : '',
            wordCount: 0,
            readTimeMinutes: 1
        });
    }
})();
