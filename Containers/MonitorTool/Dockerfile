FROM python:3.9-alpine

WORKDIR /app

# Install required packages
RUN pip install pymongo pandas

COPY monitor.py .

CMD ["python", "monitor.py"]
