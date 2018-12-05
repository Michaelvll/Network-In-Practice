#!/usr/bin/env python
'''Setuptools params'''

from setuptools import setup, find_packages

setup(
    name='cs640',
    version='0.0.0',
    description='Network controller for CS640 Assignments',
    author='',
    author_email='huangty@stanford.edu agember@cs.wisc.edu',
    packages=find_packages(exclude='test'),
    long_description="""\
Insert longer description here.
      """,
      classifiers=[
          "License :: OSI Approved :: GNU General Public License (GPL)",
          "Programming Language :: Python",
          "Development Status :: 1 - Planning",
          "Intended Audience :: Developers",
          "Topic :: Internet",
      ],
      keywords='stanford cs144 uw-madison cs640',
      license='GPL',
      install_requires=[
        'setuptools',
        'twisted',
        'ltprotocol', # David Underhill's nice Length-Type protocol handler
      ])

