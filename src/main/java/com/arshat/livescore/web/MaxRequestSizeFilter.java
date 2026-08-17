package com.arshat.livescore.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects requests whose body exceeds the configured limit. Content-Length is
 * checked up front for well-behaved clients, but chunked bodies without a
 * Content-Length header are also caught by wrapping the input stream and
 * counting bytes as they're actually read; {@link RequestTooLargeException}
 * then surfaces through message conversion and is translated to 413 by
 * {@link GlobalExceptionHandler}.
 */
@Component
public class MaxRequestSizeFilter extends OncePerRequestFilter {

    private final long maxRequestSizeBytes;

    public MaxRequestSizeFilter(@Value("${app.security.max-request-size-bytes:2097152}") long maxRequestSizeBytes) {
        this.maxRequestSizeBytes = maxRequestSizeBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxRequestSizeBytes) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Request body exceeds maximum allowed size of " + maxRequestSizeBytes + " bytes");
            return;
        }
        filterChain.doFilter(new SizeLimitingRequestWrapper(request, maxRequestSizeBytes), response);
    }

    private static final class SizeLimitingRequestWrapper extends HttpServletRequestWrapper {
        private final long maxBytes;

        SizeLimitingRequestWrapper(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new SizeLimitingInputStream(super.getInputStream(), maxBytes);
        }
    }

    private static final class SizeLimitingInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long maxBytes;
        private long bytesRead = 0;

        SizeLimitingInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                checkLimit(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = delegate.read(b, off, len);
            if (read > 0) {
                checkLimit(read);
            }
            return read;
        }

        private void checkLimit(int justRead) {
            bytesRead += justRead;
            if (bytesRead > maxBytes) {
                throw new RequestTooLargeException(
                        "Request body exceeds maximum allowed size of " + maxBytes + " bytes");
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
