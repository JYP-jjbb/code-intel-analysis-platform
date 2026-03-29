FROM ubuntu:22.04

WORKDIR /home


# Install software

RUN apt-get update
RUN apt-get install -y wget
RUN apt-get install -y unzip
RUN apt-get install -y cmake
RUN apt-get install -y software-properties-common

RUN add-apt-repository -y ppa:openjdk-r/ppa

RUN apt-get update
RUN apt-get install -y openjdk-11-jdk

RUN apt-get install -y python3-pip


# Install PIP packages

RUN pip3 install sympy
RUN pip install pyjnius
RUN pip install matplotlib
RUN pip3 install datetime
RUN pip3 install pandas
RUN pip3 install numpy
RUN pip3 install tqdm
RUN pip3 install termcolor
RUN pip3 install psutil


RUN pip install torch==1.11.0 torchvision==0.12.0 torchaudio==0.11.0 --extra-index-url https://download.pytorch.org/whl/cpu

RUN pip3 install PyYAML==6.0.3
RUN pip3 install openai==2.28.0

# Get files
ADD . /home/



# SETUP
ENV JAVAC=javac
ENV PYTHON3=python3
