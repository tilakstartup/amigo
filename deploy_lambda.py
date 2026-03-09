#!/usr/bin/env python3
import boto3
import zipfile
import io
import sys

# Read the updated lambda_function.py
with open('infrastructure/lambda/bedrock-proxy/lambda_function.py', 'r') as f:
    lambda_code = f.read()

# Create a ZIP file with the Lambda code
# Must be named 'index.py' to match the handler config 'index.lambda_handler'
zip_buffer = io.BytesIO()
with zipfile.ZipFile(zip_buffer, 'w', zipfile.ZIP_DEFLATED) as zip_file:
    zip_file.writestr('index.py', lambda_code)
zip_buffer.seek(0)
zip_content = zip_buffer.read()

# Deploy using boto3
lambda_client = boto3.client('lambda', region_name='us-east-1')

try:
    # Try to update existing function
    response = lambda_client.update_function_code(
        FunctionName='amigo-bedrock-proxy-dev',
        ZipFile=zip_content
    )
    print("✅ Lambda function updated successfully")
    print(f"Function: {response['FunctionName']}")
    print(f"Runtime: {response['Runtime']}")
    print(f"Version: {response['Version']}")
    
    # Wait for update to complete
    waiter = lambda_client.get_waiter('function_updated_v2')
    waiter.wait(FunctionName='amigo-bedrock-proxy-dev')
    print("✅ Lambda update complete and ready")
    
except lambda_client.exceptions.ResourceNotFoundException:
    print("❌ Lambda function 'amigo-bedrock-proxy-dev' not found")
    sys.exit(1)
except Exception as e:
    print(f"❌ Error updating Lambda: {e}")
    sys.exit(1)
