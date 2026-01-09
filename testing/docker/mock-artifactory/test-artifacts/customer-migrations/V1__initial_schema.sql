-- Initial schema for customer domain
CREATE TABLE IF NOT EXISTS customers (
    id SERIAL PRIMARY KEY,
    customer_code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index on email for faster lookups
CREATE INDEX IF NOT EXISTS idx_customers_email ON customers(email);
CREATE INDEX IF NOT EXISTS idx_customers_code ON customers(customer_code);

-- Insert sample data
INSERT INTO customers (customer_code, name, email, phone) VALUES
    ('CUST001', 'John Doe', 'john.doe@example.com', '555-0101'),
    ('CUST002', 'Jane Smith', 'jane.smith@example.com', '555-0102')
ON CONFLICT (email) DO NOTHING;
