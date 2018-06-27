package com.walmartlabs.concord.db;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ResultSetInputStream extends InputStream {

    private static final Logger log = LoggerFactory.getLogger(ResultSetInputStream.class);

    public static InputStream open(Connection conn, PreparedStatement ps, int columnIndex) throws SQLException {
        ResultSet rs = null; // NOSONAR
        try {
            rs = ps.executeQuery();
            if (!rs.next()) {
                closeSilently(rs);
                return null;
            }
        } catch (SQLException e) {
            closeSilently(rs);
            throw e;
        }
        return new ResultSetInputStream(conn, rs, columnIndex);
    }

    private static void closeSilently(AutoCloseable c) {
        if (c == null) {
            return;
        }

        try {
            c.close();
        } catch (Exception e) {
            log.error("Error while closing a resource", e);
        }
    }

    private final Connection conn;
    private final ResultSet rs;
    private final int columnIndex;

    private InputStream delegate;

    private ResultSetInputStream(Connection conn, ResultSet rs, int columnIndex) {
        this.conn = conn;
        this.rs = rs;
        this.columnIndex = columnIndex;
    }

    @Override
    public int read() throws IOException {
        return ensureDelegate().read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return ensureDelegate().read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return ensureDelegate().read(b, off, len);
    }

    @Override
    public int available() throws IOException {
        return ensureDelegate().available();
    }

    @Override
    public long skip(long n) throws IOException {
        return ensureDelegate().skip(n);
    }

    @Override
    public synchronized void mark(int readlimit) {
        try {
            ensureDelegate().mark(readlimit);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        ensureDelegate().reset();
    }

    @Override
    public boolean markSupported() {
        try {
            return ensureDelegate().markSupported();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private InputStream ensureDelegate() throws IOException {
        if (delegate == null) {
            try {
                delegate = rs.getBinaryStream(columnIndex);
            } catch (SQLException e) {
                throw new IOException("Can't open a stream", e);
            }
        }
        return delegate;
    }

    @Override
    public void close() throws IOException {
        closeSilently(delegate);
        closeSilently(rs);
        closeSilently(conn);
    }
}
